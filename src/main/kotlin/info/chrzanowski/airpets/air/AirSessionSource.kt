package info.chrzanowski.airpets.air

import com.intellij.air.frontend.session.catalog.SessionDiscoveryState
import com.intellij.air.frontend.session.catalog.SessionSummary
import com.intellij.air.frontend.session.catalog.SessionsSnapshot
import com.intellij.air.frontend.session.catalog.WorktreeWorkspace
import com.intellij.air.frontend.session.toolwindow.ui.AgentThreadsToolWindowModelRpcFrontendService
import com.intellij.air.shared.core.AgentSessionActivity
import com.intellij.air.shared.core.identity.LocalSessionIdentity
import com.intellij.air.shared.core.thread.AgentThreadCostKind
import com.intellij.air.shared.threads.AgentEntryPoint
import com.intellij.air.shared.threads.model.AgentSessionActivityOccurrenceKind
import com.intellij.air.thread.view.AgentSessionLinkNavigationService
import com.intellij.air.thread.view.AgentSessionLinkTarget
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import info.chrzanowski.airpets.model.PetSession
import info.chrzanowski.airpets.model.PetState
import info.chrzanowski.airpets.model.SessionCost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class AirSessionSource internal constructor(
    private val snapshots: () -> Flow<SessionsSnapshot>,
    private val navigation: () -> AgentSessionLinkNavigationService?,
    private val archive: (Project, LocalSessionIdentity, (Boolean) -> Unit) -> Boolean = ::archiveSession,
    private val archiveTimeoutMs: Long = 30_000,
) {
    constructor() : this(
        { SessionDiscoveryState.getInstance().sessions },
        { AgentSessionLinkNavigationService.getInstanceOrNull() },
    )

    private data class SessionIndex(
        val basePath: String?,
        val summaries: Map<String, SessionSummary>,
        val completed: Set<LocalSessionIdentity>,
    )

    private data class Workspace(
        val projectDirectory: String?,
        val sessions: List<SessionSummary>,
        val folders: List<Any>,
        val branch: String? = null,
    )

    private val indexes = Collections.synchronizedMap(WeakHashMap<Project, SessionIndex>())

    fun sessions(project: Project): Flow<List<PetSession>> = flow {
        snapshots().collect { snapshot ->
            val basePath = project.basePath
            val workspaces = selectWorkspaces(snapshot, basePath)
            val completed = workspaces.flatMap { it.folders }
                .filter { it.folderStatus() == "DONE" }
                .flatMap { it.folderSessions() }
                .toSet()
            val summaries = workspaces.flatMap { it.sessions }
                .filterNot { it.archived }
                .associateBy { it.petId() }
            indexes[project] = SessionIndex(basePath, summaries, completed)
            emit(summaries.map { (id, summary) ->
                val state = summary.petState(completed)
                val path = summary.localSessionIdentity.projectDirectory.takeIf { it.isNotBlank() }
                val workspace = workspaces.firstOrNull { FileUtil.pathsEqual(path, it.projectDirectory) }
                PetSession(
                    id, summary.title, state, summary.updatedAtMs, summary.detail(state), summary.sessionCost(),
                    workspacePath = path,
                    worktreeBranch = workspace?.branch,
                )
            })
        }
    }

    fun open(project: Project, id: String): Boolean {
        val index = currentIndex(project) ?: return false
        val summary = index.summaries[id] ?: return false
        val service = navigation() ?: return false
        service.openSession(
            AgentSessionLinkTarget(
                summary.agentId,
                summary.localSessionIdentity.localSessionId.value,
                summary.agentSessionId,
            ),
            project,
        )
        return true
    }

    internal fun quickChatTarget(project: Project, id: String): AirQuickChatTarget? {
        val summary = currentIndex(project)?.summaries?.get(id) ?: return null
        return nativeChatTarget(summary)
    }

    suspend fun markDone(project: Project, id: String): Boolean {
        val index = currentIndex(project) ?: return false
        val summary = index.summaries[id] ?: return false
        if (summary.petState(index.completed) != PetState.DONE) return false
        return withTimeoutOrNull(archiveTimeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                val complete: (Boolean) -> Unit = { success ->
                    if (finished.compareAndSet(false, true)) continuation.resume(success)
                }
                continuation.invokeOnCancellation { finished.set(true) }
                if (continuation.isActive && !archive(project, summary.localSessionIdentity, complete)) complete(false)
            }
        } ?: false
    }

    private fun currentIndex(project: Project): SessionIndex? {
        if (project.isDisposed) return null
        val index = indexes[project] ?: return null
        val basePath = project.basePath?.takeIf { it.isNotBlank() } ?: return null
        return index.takeIf { FileUtil.pathsEqual(basePath, it.basePath) }
    }

    private fun selectWorkspaces(snapshot: SessionsSnapshot, basePath: String?): List<Workspace> {
        if (basePath.isNullOrBlank()) return emptyList()
        val roots = snapshot.projects.filter { FileUtil.pathsEqual(basePath, it.projectDirectory?.value) }
        return if (roots.isNotEmpty()) {
            roots.flatMap { root ->
                listOf(Workspace(root.projectDirectory?.value, root.sessions, root.folders)) +
                    root.worktrees.map { it.toWorkspace() }
            }
        } else {
            snapshot.projects.flatMap { it.worktrees }
                .filter { FileUtil.pathsEqual(basePath, it.projectDirectory?.value) }
                .map { it.toWorkspace() }
        }
    }

    private fun WorktreeWorkspace.toWorkspace(): Workspace = Workspace(projectDirectory?.value, sessions, folders, branch)

    private fun SessionSummary.petId(): String {
        val identity = localSessionIdentity
        return "air:${identity.projectDirectory.length}:${identity.projectDirectory}:${identity.localSessionId.value}"
    }

    private fun SessionSummary.sessionCost(): SessionCost? {
        val cost = cost ?: return null
        val amountUsd = cost.amountUsd ?: return null
        if (amountUsd.signum() < 0) return null
        val estimated = when (cost.kind) {
            AgentThreadCostKind.EXACT -> false
            AgentThreadCostKind.ESTIMATED -> true
            AgentThreadCostKind.UNAVAILABLE -> return null
        }
        return SessionCost(amountUsd, estimated)
    }

    private fun SessionSummary.petState(completed: Set<LocalSessionIdentity>): PetState = when {
        activity == AgentSessionActivity.PROCESSING -> PetState.WORKING
        activity == AgentSessionActivity.NEEDS_INPUT -> PetState.WAITING
        activity == AgentSessionActivity.REVIEWING -> PetState.REVIEW
        isWaitingChainedSession -> PetState.WAITING
        stoppedWithError -> PetState.ERROR
        activityOccurrence?.kind == AgentSessionActivityOccurrenceKind.NEEDS_ATTENTION -> PetState.WAITING
        activity == AgentSessionActivity.UNREAD ||
            activityOccurrence?.kind == AgentSessionActivityOccurrenceKind.COMPLETED ||
            localSessionIdentity in completed -> PetState.DONE
        else -> PetState.RESTING
    }

    private fun Any.folderStatus(): String? = readFolderProperty("Status")?.let {
        if (it is Enum<*>) it.name else it.toString()
    }

    private fun Any.folderSessions(): List<LocalSessionIdentity> =
        (readFolderProperty("Sessions") as? Iterable<*>)?.filterIsInstance<LocalSessionIdentity>().orEmpty()

    private fun Any.readFolderProperty(name: String): Any? = javaClass.methods
        .firstOrNull { it.name == "get$name" && it.parameterCount == 0 }
        ?.invoke(this)

    private fun SessionSummary.detail(state: PetState): String = when (state) {
        PetState.WORKING -> "Air · processing"
        PetState.WAITING -> if (isWaitingChainedSession) "Air · waiting for another session" else "Air · needs input"
        PetState.REVIEW -> "Air · reviewing"
        PetState.RESTING -> "Air · ready or paused"
        PetState.DONE -> "Air · completed"
        PetState.ERROR -> "Air · stopped with an error"
    }
}

private fun archiveSession(project: Project, identity: LocalSessionIdentity, complete: (Boolean) -> Unit): Boolean {
    val service = ApplicationManager.getApplication()
        .getService(AgentThreadsToolWindowModelRpcFrontendService::class.java) ?: return false
    service.archiveSessions(project, listOf(identity), AgentEntryPoint.TREE_ROW_OVERLAY) { result ->
        complete(result.allRequestedArchived)
    }
    return true
}