package info.chrzanowski.airpets.air

import com.intellij.air.frontend.session.catalog.PathRef
import com.intellij.air.frontend.session.catalog.ProjectWorkspace
import com.intellij.air.frontend.session.catalog.SessionFolder
import com.intellij.air.frontend.session.catalog.SessionFolderStatus
import com.intellij.air.frontend.session.catalog.SessionId
import com.intellij.air.frontend.session.catalog.SessionSummary
import com.intellij.air.frontend.session.catalog.SessionsSnapshot
import com.intellij.air.frontend.session.catalog.WorktreeWorkspace
import com.intellij.air.shared.core.AgentSessionActivity
import com.intellij.air.shared.core.AgentSessionAggregateExclusion
import com.intellij.air.shared.core.identity.LocalSessionId
import com.intellij.air.shared.core.identity.LocalSessionIdentity
import com.intellij.air.shared.core.identity.SessionWorkspaceId
import com.intellij.air.shared.core.thread.AgentId
import com.intellij.air.shared.core.thread.AgentLaunchTargetId
import com.intellij.air.shared.core.thread.AgentThreadCost
import com.intellij.air.shared.core.thread.AgentThreadCostKind
import com.intellij.air.shared.session.SessionFolderId
import com.intellij.air.shared.threads.model.AgentSessionActivityOccurrence
import com.intellij.air.shared.threads.model.AgentSessionActivityOccurrenceKind
import com.intellij.air.thread.view.AgentSessionLinkNavigationService
import com.intellij.air.thread.view.AgentSessionLinkTarget
import com.intellij.mock.MockProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import info.chrzanowski.airpets.model.PetSession
import info.chrzanowski.airpets.model.PetState
import info.chrzanowski.airpets.model.SessionCost
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AirSessionSourceTest {
    private val disposable = Disposer.newDisposable()

    @After
    fun tearDown() {
        Disposer.dispose(disposable)
    }

    @Test
    fun `maps every frontend activity using typed AIR summaries`() {
        val expected = mapOf(
            AgentSessionActivity.READY to PetState.RESTING,
            AgentSessionActivity.DRAFT to PetState.RESTING,
            AgentSessionActivity.PROCESSING to PetState.WORKING,
            AgentSessionActivity.REVIEWING to PetState.REVIEW,
            AgentSessionActivity.NEEDS_INPUT to PetState.WAITING,
            AgentSessionActivity.UNREAD to PetState.DONE,
        )
        assertEquals(AgentSessionActivity.entries.toSet(), expected.keys)
        expected.forEach { (activity, state) ->
            val pet = pets(summary().copy(activity = activity)).single()
            assertEquals(activity.name, state, pet.state)
            assertEquals("Session title", pet.title)
            assertEquals(123L, pet.updatedAtMs)
            assertTrue(pet.detail.startsWith("AIR · "))
        }
    }

    @Test
    fun `live activity beats stale errors completion occurrences and done folders`() {
        listOf(
            AgentSessionActivity.PROCESSING to PetState.WORKING,
            AgentSessionActivity.NEEDS_INPUT to PetState.WAITING,
            AgentSessionActivity.REVIEWING to PetState.REVIEW,
        ).forEach { (activity, state) ->
            val session = summary().copy(
                activity = activity,
                stoppedWithError = true,
                activityOccurrence = occurrence(AgentSessionActivityOccurrenceKind.COMPLETED),
                aggregateExclusion = AgentSessionAggregateExclusion.WAITING_CHAINED_SESSION,
            )
            assertEquals(state, pets(session, folders = listOf(folder(session))).single().state)
        }
    }

    @Test
    fun `stopped errors beat non-live completion signals`() {
        val session = summary().copy(
            activity = AgentSessionActivity.UNREAD,
            stoppedWithError = true,
            activityOccurrence = occurrence(AgentSessionActivityOccurrenceKind.COMPLETED),
        )
        assertEquals(PetState.ERROR, pets(session, folders = listOf(folder(session))).single().state)
    }

    @Test
    fun `completion occurrence and done folder each indicate done`() {
        val session = summary()
        assertEquals(
            PetState.DONE,
            pets(session.copy(activityOccurrence = occurrence(AgentSessionActivityOccurrenceKind.COMPLETED))).single().state,
        )
        assertEquals(PetState.DONE, pets(session, folders = listOf(folder(session))).single().state)
        assertEquals(
            PetState.RESTING,
            pets(
                session,
                folders = listOf(folder(session).copy(status = SessionFolderStatus.IN_PROGRESS)),
            ).single().state,
        )
    }

    @Test
    fun `attention and chained waiting beat completion`() {
        val session =
            summary().copy(activityOccurrence = occurrence(AgentSessionActivityOccurrenceKind.NEEDS_ATTENTION))
        assertEquals(PetState.WAITING, pets(session, folders = listOf(folder(session))).single().state)
        val chained = session.copy(
            aggregateExclusion = AgentSessionAggregateExclusion.WAITING_CHAINED_SESSION,
            activityOccurrence = occurrence(AgentSessionActivityOccurrenceKind.COMPLETED),
        )
        val pet = pets(chained, folders = listOf(folder(chained))).single()
        assertEquals(PetState.WAITING, pet.state)
        assertTrue(pet.detail.contains("another session"))
    }

    @Test
    fun `root project includes its worktrees but never unrelated projects`() {
        val snapshot = workspaceSnapshot()
        val source = source(snapshot)
        val pets = collect(source, TestProject("$ROOT/"))
        assertEquals(setOf("root", "tree", "sibling"), pets.map { it.title }.toSet())
        assertEquals(PetState.DONE, pets.single { it.title == "tree" }.state)
        assertEquals(PetState.RESTING, pets.single { it.title == "root" }.state)
    }

    @Test
    fun `separately opened worktree includes only that workspace`() {
        val pets = collect(source(workspaceSnapshot()), TestProject(TREE))
        assertEquals(listOf("tree"), pets.map { it.title })
        assertEquals(PetState.DONE, pets.single().state)
    }

    @Test
    fun `missing blank unmatched and parent paths cannot leak sessions`() {
        val snapshot = workspaceSnapshot()
        listOf(null, "", " ", "/workspace", "/unknown").forEach { path ->
            assertTrue(collect(source(snapshot), TestProject(path)).isEmpty())
        }
        val pathless = root(listOf(summary())).copy(projectDirectory = null)
        assertTrue(collect(source(SessionsSnapshot(listOf(pathless))), TestProject(ROOT)).isEmpty())
    }

    @Test
    fun `archived sessions are removed from both pets and navigation`() {
        val live = summary()
        val project = TestProject(ROOT)
        val navigation = RecordingNavigation()
        val source = source(
            SessionsSnapshot(listOf(root(listOf(live)))),
            SessionsSnapshot(listOf(root(listOf(live.copy(archived = true))))),
            navigation = navigation,
        )
        val emissions = runBlocking { source.sessions(project).toList() }
        assertEquals(1, emissions.first().size)
        assertTrue(emissions.last().isEmpty())
        assertFalse(source.open(project, emissions.first().single().id))
        assertTrue(navigation.opened.isEmpty())
    }

    @Test
    fun `ids remain stable as routing changes and navigation uses the latest summary`() {
        val first = summary().copy(agentSessionId = null)
        val latest = first.copy(
            id = SessionId(
                SessionWorkspaceId("new-workspace"),
                AgentId.from("codex"),
                AgentLaunchTargetId.from("local"),
                "new-provider-id",
            ),
            agentSessionId = "backend-session-id",
        )
        val navigation = RecordingNavigation()
        val source = source(
            SessionsSnapshot(listOf(root(listOf(first)))),
            SessionsSnapshot(listOf(root(listOf(latest)))),
            navigation = navigation,
        )
        val project = TestProject(ROOT)
        val emissions = runBlocking { source.sessions(project).toList() }
        assertEquals(emissions.first(), emissions.last())
        assertTrue(source.open(project, emissions.first().single().id))
        val (target, openedProject) = navigation.opened.single()
        assertSame(project, openedProject)
        assertEquals(AgentId.from("codex"), target.agentId)
        assertEquals(first.localSessionIdentity.localSessionId.value, target.localSessionId)
        assertEquals("backend-session-id", target.agentSessionId)
    }

    @Test
    fun `navigation accepts local-only sessions and returns false for unavailable targets`() {
        val snapshot = SessionsSnapshot(listOf(root(listOf(summary()))))
        val navigation = RecordingNavigation()
        val source = source(snapshot, navigation = navigation)
        val project = TestProject(ROOT)
        assertFalse(source.open(project, "unknown"))
        val id = collect(source, project).single().id
        assertFalse(source.open(project, "unknown"))
        assertTrue(source.open(project, id))
        assertEquals(null, navigation.opened.single().first.agentSessionId)
        val unavailable = source(snapshot)
        val unavailableId = collect(unavailable, project).single().id
        assertFalse(unavailable.open(project, unavailableId))
        assertFalse(source.open(TestProject("/unrelated"), id))
        project.path = "/unrelated"
        assertFalse(source.open(project, id))
    }

    @Test
    fun `navigation indexes are isolated across projects and replaced on every emission`() {
        val snapshot = workspaceSnapshot()
        val navigation = RecordingNavigation()
        val source = source(snapshot, navigation = navigation)
        val rootProject = TestProject(ROOT)
        val otherProject = TestProject("/other")
        val rootId = collect(source, rootProject).first().id
        val otherId = collect(source, otherProject).single().id
        assertTrue(source.open(rootProject, rootId))
        assertTrue(source.open(otherProject, otherId))
        assertFalse(source.open(rootProject, otherId))
        assertFalse(source.open(otherProject, rootId))
        val removed = source(snapshot, SessionsSnapshot(), navigation = navigation)
        val emissions = runBlocking { removed.sessions(rootProject).toList() }
        assertFalse(removed.open(rootProject, emissions.first().first().id))
    }

    @Test
    fun `identity includes the directory and cannot collide through delimiters`() {
        val root = summary(localId = "a:b", path = ROOT)
        val tree = summary(localId = "b", path = "$ROOT:a")
        val snapshot =
            SessionsSnapshot(listOf(root(listOf(root), worktrees = listOf(worktree("$ROOT:a", listOf(tree))))))
        val pets = collect(source(snapshot), TestProject(ROOT))
        assertEquals(2, pets.size)
        assertNotEquals(pets[0].id, pets[1].id)
        val sameLocalId = SessionsSnapshot(
            listOf(
                root(
                    listOf(root),
                    worktrees = listOf(worktree(TREE, listOf(summary("a:b", TREE)))),
                ),
            ),
        )
        assertEquals(2, collect(source(sameLocalId), TestProject(ROOT)).map { it.id }.toSet().size)
    }

    @Test
    fun `done folders match the entire local identity and do not leak from other workspaces`() {
        val session = summary()
        val unrelated = session.copy(
            localSessionIdentity = LocalSessionIdentity(
                "/other",
                session.localSessionIdentity.localSessionId,
            ),
        )
        assertEquals(PetState.RESTING, pets(session, folders = listOf(folder(unrelated))).single().state)
        val snapshot = SessionsSnapshot(
            listOf(
                root(listOf(session)),
                root(emptyList(), path = "/other", folders = listOf(folder(session))),
            ),
        )
        assertEquals(PetState.RESTING, collect(source(snapshot), TestProject(ROOT)).single().state)
    }

    @Test
    fun `mark done uses the exact indexed root and worktree identities without opening`() = runBlocking {
        val rootSession = summary().copy(activity = AgentSessionActivity.UNREAD)
        val treeSession =
            summary(path = TREE).copy(activityOccurrence = occurrence(AgentSessionActivityOccurrenceKind.COMPLETED))
        val snapshot =
            SessionsSnapshot(listOf(root(listOf(rootSession), worktrees = listOf(worktree(TREE, listOf(treeSession))))))
        val project = TestProject(ROOT)
        val navigation = RecordingNavigation()
        val archived = mutableListOf<Pair<Project, LocalSessionIdentity>>()
        val source = AirSessionSource(
            { flowOf(snapshot) }, { navigation },
            archive = { target, identity, complete ->
                archived += target to identity
                complete(true)
                true
            },
        )
        val pets = source.sessions(project).toList().single()
        pets.forEach { assertTrue(source.markDone(project, it.id)) }
        assertEquals(
            listOf(project to rootSession.localSessionIdentity, project to treeSession.localSessionIdentity),
            archived,
        )
        assertTrue("The finish action must not open the session", navigation.opened.isEmpty())
    }

    @Test
    fun `mark done rejects active stale unknown and foreign sessions`() = runBlocking {
        var calls = 0
        val session = summary().copy(activity = AgentSessionActivity.UNREAD)
        var snapshot = SessionsSnapshot(listOf(root(listOf(session))))
        val source = AirSessionSource(
            { flowOf(snapshot) }, { null },
            archive = { _, _, complete ->
                calls++
                complete(true)
                true
            },
        )
        val project = TestProject(ROOT)
        val id = source.sessions(project).toList().single().single().id
        assertFalse(source.markDone(project, "missing"))
        assertFalse(source.markDone(TestProject(ROOT), id))
        val other = TestProject("/other")
        source.sessions(other).toList()
        assertFalse(source.markDone(other, id))
        project.path = TREE
        assertFalse(source.markDone(project, id))
        project.path = ROOT
        for (activity in AgentSessionActivity.entries.filterNot { it == AgentSessionActivity.UNREAD }) {
            snapshot = SessionsSnapshot(listOf(root(listOf(session.copy(activity = activity)))))
            source.sessions(project).toList()
            assertFalse("$activity must not finish from a stale heart", source.markDone(project, id))
        }
        snapshot = SessionsSnapshot(
            listOf(
                root(
                    listOf(session.copy(activity = AgentSessionActivity.PROCESSING)),
                    folders = listOf(folder(session)),
                ),
            ),
        )
        source.sessions(project).toList()
        assertFalse(source.markDone(project, id))
        snapshot = SessionsSnapshot(listOf(root(listOf(session.copy(archived = true)))))
        source.sessions(project).toList()
        assertFalse(source.markDone(project, id))
        snapshot = SessionsSnapshot()
        source.sessions(project).toList()
        assertFalse(source.markDone(project, id))
        assertEquals(0, calls)
    }

    @Test
    fun `mark done requires AIR success and does not hide an unconfirmed session`() = runBlocking {
        val snapshot = SessionsSnapshot(listOf(root(listOf(summary().copy(activity = AgentSessionActivity.UNREAD)))))
        val project = TestProject(ROOT)
        for (available in listOf(false, true)) {
            val source = AirSessionSource(
                { flowOf(snapshot) }, { null },
                archive = { _, _, complete ->
                    if (available) complete(false)
                    available
                },
            )
            val pet = source.sessions(project).toList().single().single()
            assertFalse(source.markDone(project, pet.id))
            assertEquals(pet, source.sessions(project).toList().single().single())
        }
    }

    @Test
    fun `mark done times out and ignores duplicate or late callbacks`() = runBlocking {
        val snapshot = SessionsSnapshot(listOf(root(listOf(summary().copy(activity = AgentSessionActivity.UNREAD)))))
        val project = TestProject(ROOT)
        var callback: ((Boolean) -> Unit)? = null
        val source = AirSessionSource(
            { flowOf(snapshot) }, { null },
            archive = { _, _, complete ->
                callback = complete
                true
            },
            archiveTimeoutMs = 20,
        )
        val id = source.sessions(project).toList().single().single().id
        assertFalse(source.markDone(project, id))
        requireNotNull(callback)(true)
        requireNotNull(callback)(false)
        val duplicated = AirSessionSource(
            { flowOf(snapshot) }, { null },
            archive = { _, _, complete ->
                complete(true)
                complete(false)
                true
            },
        )
        duplicated.sessions(project).toList()
        assertTrue(duplicated.markDone(project, id))
    }

    @Test
    fun `mark done cancellation does not wait for AIR callback`() = runBlocking {
        val snapshot = SessionsSnapshot(listOf(root(listOf(summary().copy(activity = AgentSessionActivity.UNREAD)))))
        val project = TestProject(ROOT)
        val callback = CompletableDeferred<(Boolean) -> Unit>()
        val source = AirSessionSource(
            { flowOf(snapshot) }, { null },
            archive = { _, _, complete ->
                callback.complete(complete)
                true
            },
        )
        val id = source.sessions(project).toList().single().single().id
        val request = async(start = CoroutineStart.UNDISPATCHED) { source.markDone(project, id) }
        val complete = callback.await()
        request.cancelAndJoin()
        assertTrue(request.isCancelled)
        complete(true)
    }

    @Test
    fun `maps exact and estimated session costs without losing precision`() {
        val exactAmount = BigDecimal("0.012300")
        val estimatedAmount = BigDecimal("1234567890.123456789")
        val exact = pets(summary().copy(cost = AgentThreadCost(exactAmount, AgentThreadCostKind.EXACT))).single()
        val estimated =
            pets(summary().copy(cost = AgentThreadCost(estimatedAmount, AgentThreadCostKind.ESTIMATED))).single()

        assertEquals(SessionCost(exactAmount), exact.cost)
        assertEquals(exactAmount, exact.cost?.amountUsd)
        assertFalse(requireNotNull(exact.cost).estimated)
        assertEquals(SessionCost(estimatedAmount, estimated = true), estimated.cost)
        assertEquals(estimatedAmount, estimated.cost?.amountUsd)
    }

    @Test
    fun `does not map unavailable or invalid session costs`() {
        val missingCost = pets(summary()).single()
        val missingAmount = pets(summary().copy(cost = AgentThreadCost(null, AgentThreadCostKind.EXACT))).single()
        val unavailable =
            pets(summary().copy(cost = AgentThreadCost(BigDecimal("2.50"), AgentThreadCostKind.UNAVAILABLE))).single()
        val negative =
            pets(summary().copy(cost = AgentThreadCost(BigDecimal("-0.01"), AgentThreadCostKind.EXACT))).single()
        val zero = pets(summary().copy(cost = AgentThreadCost(BigDecimal.ZERO, AgentThreadCostKind.EXACT))).single()

        assertEquals(null, missingCost.cost)
        assertEquals(null, missingAmount.cost)
        assertEquals(null, unavailable.cost)
        assertEquals(null, negative.cost)
        assertEquals(SessionCost(BigDecimal.ZERO), zero.cost)
    }

    @Test
    fun `updated session cost flows through for the same session identity`() = runBlocking {
        val firstAmount = BigDecimal("0.10")
        val updatedAmount = BigDecimal("0.25")
        val session = summary().copy(cost = AgentThreadCost(firstAmount, AgentThreadCostKind.EXACT))
        val snapshot = MutableStateFlow(SessionsSnapshot(listOf(root(listOf(session)))))
        val project = TestProject(ROOT)
        val source = AirSessionSource({ snapshot }, { null })
        val emissions = mutableListOf<List<PetSession>>()
        val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
            source.sessions(project).take(2).toList(emissions)
        }

        snapshot.value = SessionsSnapshot(
            listOf(
                root(
                    listOf(
                        session.copy(cost = AgentThreadCost(updatedAmount, AgentThreadCostKind.ESTIMATED)),
                    ),
                ),
            ),
        )
        collecting.join()

        assertEquals(2, emissions.size)
        assertEquals(emissions[0].single().id, emissions[1].single().id)
        assertEquals(SessionCost(firstAmount), emissions[0].single().cost)
        assertEquals(SessionCost(updatedAmount, estimated = true), emissions[1].single().cost)
    }

    @Test
    fun `archiving through mark done removes the pet only after a discovery update`() = runBlocking {
        val session = summary().copy(activity = AgentSessionActivity.UNREAD)
        val snapshot = MutableStateFlow(SessionsSnapshot(listOf(root(listOf(session)))))
        val project = TestProject(ROOT)
        var complete: ((Boolean) -> Unit)? = null
        val source = AirSessionSource(
            { snapshot }, { null },
            archive = { _, _, callback ->
                complete = callback
                true
            },
        )
        val emissions = mutableListOf<List<PetSession>>()
        val collecting =
            launch(start = CoroutineStart.UNDISPATCHED) { source.sessions(project).take(2).toList(emissions) }
        val id = emissions.single().single().id
        val marking = async(start = CoroutineStart.UNDISPATCHED) { source.markDone(project, id) }
        assertEquals(1, emissions.size)
        assertFalse(marking.isCompleted)
        snapshot.value = SessionsSnapshot(listOf(root(listOf(session.copy(archived = true)))))
        requireNotNull(complete)(true)
        assertTrue(marking.await())
        collecting.join()
        assertTrue(emissions.last().isEmpty())
        assertFalse(source.markDone(project, id))
    }

    private fun pets(session: SessionSummary, folders: List<SessionFolder> = emptyList()): List<PetSession> =
        collect(source(SessionsSnapshot(listOf(root(listOf(session), folders = folders)))), TestProject(ROOT))

    private fun collect(source: AirSessionSource, project: Project): List<PetSession> = runBlocking {
        source.sessions(project).toList().last()
    }

    private fun source(
        vararg snapshots: SessionsSnapshot,
        navigation: AgentSessionLinkNavigationService? = null,
    ): AirSessionSource = AirSessionSource({ flowOf(*snapshots) }, { navigation })

    private fun summary(localId: String = "session", path: String = ROOT): SessionSummary = SessionSummary(
        id = SessionId(SessionWorkspaceId(path), AgentId.from("junie"), AgentLaunchTargetId.from("local"), localId),
        title = "Session title",
        updatedAtMs = 123L,
        archived = false,
        activity = AgentSessionActivity.READY,
        localSessionIdentity = LocalSessionIdentity(path, LocalSessionId(localId)),
    )

    private fun occurrence(kind: AgentSessionActivityOccurrenceKind) = AgentSessionActivityOccurrence(kind, "revision")

    private fun folder(session: SessionSummary) = SessionFolder(
        id = SessionFolderId("done-folder"),
        workspaceId = SessionWorkspaceId(session.localSessionIdentity.projectDirectory),
        name = "Done",
        status = SessionFolderStatus.DONE,
        createdAtMs = 1L,
        updatedAtMs = 2L,
        sessions = setOf(session.localSessionIdentity),
    )

    private fun root(
        sessions: List<SessionSummary>,
        path: String = ROOT,
        worktrees: List<WorktreeWorkspace> = emptyList(),
        folders: List<SessionFolder> = emptyList(),
    ) = ProjectWorkspace(
        id = SessionWorkspaceId(path),
        projectDirectory = PathRef(path),
        displayName = path,
        isOpen = true,
        sessions = sessions,
        worktrees = worktrees,
        folders = folders,
    )

    private fun worktree(path: String, sessions: List<SessionSummary>, folders: List<SessionFolder> = emptyList()) =
        WorktreeWorkspace(
            id = SessionWorkspaceId(path),
            parentWorkspaceId = SessionWorkspaceId(ROOT),
            projectDirectory = PathRef(path),
            displayName = path,
            isOpen = true,
            branch = "feature",
            sessions = sessions,
            folders = folders,
        )

    private fun workspaceSnapshot(): SessionsSnapshot {
        val tree = summary(path = TREE).copy(title = "tree")
        val root = summary().copy(title = "root")
        val sibling = summary(path = "/trees/sibling").copy(title = "sibling")
        val archived = summary("archived", TREE).copy(title = "archived", archived = true)
        return SessionsSnapshot(
            listOf(
                root(
                    listOf(root),
                    worktrees = listOf(
                        worktree(TREE, listOf(tree, archived), listOf(folder(tree))),
                        worktree("/trees/sibling", listOf(sibling)),
                    ),
                ),
                root(
                    listOf(summary(path = "/other").copy(title = "unrelated")),
                    path = "/other",
                    folders = listOf(folder(root)),
                ),
            ),
        )
    }

    private inner class TestProject(var path: String?) : MockProject(null, disposable) {
        override fun getBasePath(): String? = path
    }

    private class RecordingNavigation : AgentSessionLinkNavigationService {
        val opened = mutableListOf<Pair<AgentSessionLinkTarget, Project?>>()

        override fun openSession(target: AgentSessionLinkTarget, project: Project?) {
            opened += target to project
        }
    }

    private companion object {
        const val ROOT = "/workspace/repo"
        const val TREE = "/trees/feature"
    }
}