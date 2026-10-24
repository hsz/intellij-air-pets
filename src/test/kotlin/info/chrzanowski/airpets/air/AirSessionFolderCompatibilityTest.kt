package info.chrzanowski.airpets.air

import com.intellij.air.frontend.session.catalog.PathRef
import com.intellij.air.frontend.session.catalog.ProjectWorkspace
import com.intellij.air.frontend.session.catalog.SessionFolder
import com.intellij.air.frontend.session.catalog.SessionId
import com.intellij.air.frontend.session.catalog.SessionSummary
import com.intellij.air.frontend.session.catalog.SessionsSnapshot
import com.intellij.air.shared.core.identity.LocalSessionId
import com.intellij.air.shared.core.identity.LocalSessionIdentity
import com.intellij.air.shared.core.identity.SessionWorkspaceId
import com.intellij.air.shared.core.thread.AgentId
import com.intellij.air.shared.core.thread.AgentLaunchTargetId
import com.intellij.mock.MockProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import info.chrzanowski.airpets.model.PetSession
import info.chrzanowski.airpets.model.PetState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AirSessionFolderCompatibilityTest {
    private val disposable = Disposer.newDisposable()

    @After
    fun tearDown() {
        Disposer.dispose(disposable)
    }

    @Test
    fun `session discovery supports relocated folder model types`() {
        val session = summary()
        val identity = session.localSessionIdentity

        assertEquals(PetState.DONE, discover(session, RelocatedFolder(RelocatedFolderStatus.DONE, setOf(identity))))
        assertEquals(PetState.RESTING, discover(session, RelocatedFolder(RelocatedFolderStatus.IN_PROGRESS, setOf(identity))))
    }

    private fun discover(session: SessionSummary, folder: RelocatedFolder): PetState {
        val loader = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
                if (name == SESSION_FOLDER || name == SESSION_FOLDER_STATUS) throw ClassNotFoundException(name)
                if (!name.startsWith(AirSessionSource::class.java.name)) return@synchronized super.loadClass(name, resolve)
                val loaded = findLoadedClass(name) ?: run {
                    val internalName = name.replace('.', '/')
                    val bytes = requireNotNull(getResourceAsStream("$internalName.class")).use { it.readBytes() }
                    defineClass(name, bytes, 0, bytes.size)
                }
                if (resolve) resolveClass(loaded)
                loaded
            }
        }
        val workspace = ProjectWorkspace(
            id = SessionWorkspaceId(ROOT),
            projectDirectory = PathRef(ROOT),
            displayName = ROOT,
            isOpen = true,
            sessions = listOf(session),
            worktrees = emptyList(),
            folders = folders(folder),
        )
        val snapshot = SessionsSnapshot(listOf(workspace))
        val sourceClass = loader.loadClass(AirSessionSource::class.java.name)
        val constructor = sourceClass.declaredConstructors.single { it.parameterCount == 4 }
        val source = constructor.newInstance(
            { flowOf(snapshot) },
            { null },
            { _: Project, _: LocalSessionIdentity, complete: (Boolean) -> Unit -> complete(false); true },
            30_000L,
        )
        @Suppress("UNCHECKED_CAST")
        val flow = sourceClass.getMethod("sessions", Project::class.java).invoke(source, TestProject()) as Flow<List<PetSession>>
        return runBlocking { flow.toList().single().single().state }
    }

    @Suppress("UNCHECKED_CAST")
    private fun folders(folder: RelocatedFolder): List<SessionFolder> = listOf(folder) as List<SessionFolder>

    private fun summary(): SessionSummary = SessionSummary(
        id = SessionId(SessionWorkspaceId(ROOT), AgentId.from("junie"), AgentLaunchTargetId.from("local"), "session"),
        title = "Session title",
        updatedAtMs = 123L,
        archived = false,
        localSessionIdentity = LocalSessionIdentity(ROOT, LocalSessionId("session")),
    )

    private inner class TestProject : MockProject(null, disposable) {
        override fun getBasePath(): String = ROOT
    }

    private companion object {
        const val ROOT = "/workspace"
        const val SESSION_FOLDER = "com.intellij.air.frontend.session.catalog.SessionFolder"
        const val SESSION_FOLDER_STATUS = "com.intellij.air.frontend.session.catalog.SessionFolderStatus"
    }
}

enum class RelocatedFolderStatus { DONE, IN_PROGRESS }

data class RelocatedFolder(val status: RelocatedFolderStatus, val sessions: Set<LocalSessionIdentity>)