package info.chrzanowski.airpets.air

import com.intellij.air.frontend.session.catalog.SessionId
import com.intellij.air.frontend.session.catalog.SessionSummary
import com.intellij.air.frontend.session.view.gui.THREAD_GUI_SEMANTIC_TEXT
import com.intellij.air.shared.core.AgentSessionActivity
import com.intellij.air.shared.core.identity.LocalSessionId
import com.intellij.air.shared.core.identity.LocalSessionIdentity
import com.intellij.air.shared.core.identity.SessionWorkspaceId
import com.intellij.air.shared.core.thread.AgentId
import com.intellij.air.shared.core.thread.AgentLaunchTargetId
import com.intellij.air.shared.session.view.gui.SessionProjectionSnapshot
import com.intellij.air.shared.session.view.gui.SessionProjectionUpdate
import com.intellij.air.shared.session.view.gui.ThreadGuiApi
import com.intellij.air.shared.session.view.gui.ThreadGuiChatEventId
import com.intellij.air.shared.session.view.gui.ThreadGuiChatUserPromptEvent
import com.intellij.air.shared.session.view.gui.ThreadGuiMetadata
import com.intellij.air.shared.session.view.gui.ThreadGuiRef
import com.intellij.air.shared.session.view.gui.ThreadGuiSnapshot
import com.intellij.air.shared.session.view.gui.ThreadGuiStatus
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.replaceService
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.forceRespectIsShowingClientProperty
import com.intellij.util.ui.withShowingChanged
import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.awt.Component
import java.awt.Container
import java.awt.event.HierarchyEvent
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent

class AirQuickChatViewTest {
    private val fixture = IdeaTestFixtureFactory.getFixtureFactory().createBareFixture()
    private lateinit var project: Project

    @Before
    fun setUp() {
        fixture.setUp()
        val path = Files.createDirectories(Path.of("build/test-native-chat").toAbsolutePath())
        project = requireNotNull(ProjectManagerEx.getInstanceEx().newProject(path, OpenProjectTask {}))
    }

    @After
    fun tearDown() {
        try {
            if (::project.isInitialized) EdtTestUtil.runInEdtAndWait<Throwable> { runWriteAction { Disposer.dispose(project) } }
        } finally {
            fixture.tearDown()
        }
    }

    @Test
    fun `installed AIR native provider mounts an independent existing-session view without showing or sending`() {
        EdtTestUtil.runInEdtAndWait<Throwable> {
            val lifetime = Disposer.newDisposable()
            val scope = CoroutineScope(SupervisorJob())
            try {
                val path = requireNotNull(project.basePath)
                val summary = SessionSummary(
                    id = SessionId(SessionWorkspaceId(path), AgentId.from("junie"), AgentLaunchTargetId.from("local"), "air-pets-test-only"),
                    title = "AIR Pets native view test",
                    updatedAtMs = 1,
                    archived = false,
                    activity = AgentSessionActivity.READY,
                    localSessionIdentity = LocalSessionIdentity(path, LocalSessionId("air-pets-local-test")),
                )
                val view = requireNotNull(createAirQuickChat(project, requireNotNull(nativeChatTarget(summary)), lifetime, scope))
                Disposer.register(lifetime, view)
                assertTrue("The native provider must install its own conversation component", view.component.componentCount > 0)
                assertNotNull("The native composer must expose a focus target", view.preferredFocusComponent)
                assertNull("The plugin must not move AIR's existing editor or toolwindow", view.component.parent)
            } finally {
                scope.cancel()
                Disposer.dispose(lifetime)
            }
        }
    }

    @Test
    fun `visible native chat requests the AIR runtime identity and renders existing history`() {
        EdtTestUtil.runInEdtAndWait<Throwable> {
            val lifetime = Disposer.newDisposable()
            val scope = CoroutineScope(SupervisorJob())
            val application = ApplicationManager.getApplication()
            val original = application.getService(RemoteApiProviderService::class.java)
            val requests = CopyOnWriteArrayList<ThreadGuiRef>()
            val runtimeId = "air-pets-runtime"
            val prompt = "Existing conversation regression message"
            val snapshot = SessionProjectionUpdate.Snapshot(SessionProjectionSnapshot(ThreadGuiSnapshot(
                metadata = ThreadGuiMetadata(runtimeId, "Existing session", ThreadGuiStatus.Completed, null, canSend = true),
                chatEvents = listOf(ThreadGuiChatUserPromptEvent(ThreadGuiChatEventId("prompt-1"), prompt)),
            )))
            application.replaceService(RemoteApiProviderService::class.java, object : RemoteApiProviderService {
                override suspend fun <T : RemoteApi<Unit>> resolve(descriptor: RemoteApiDescriptor<T>): T {
                    val api = original.resolve(descriptor)
                    if (api !is ThreadGuiApi) return api
                    @Suppress("UNCHECKED_CAST")
                    return object : ThreadGuiApi by api {
                        override suspend fun events(ref: ThreadGuiRef): Flow<SessionProjectionUpdate> {
                            requests += ref
                            return flowOf(snapshot)
                        }
                    } as T
                }
                override fun listRegisteredApis() = original.listRegisteredApis()
                override fun isServiceOperational() = original.isServiceOperational()
            }, lifetime)
            val originalShowing = forceRespectIsShowingClientProperty
            var screen: JComponent? = null
            try {
                val path = requireNotNull(project.basePath)
                val summary = SessionSummary(
                    id = SessionId(SessionWorkspaceId(path), AgentId.from("junie"), AgentLaunchTargetId.from("local"), runtimeId),
                    title = "Existing session",
                    updatedAtMs = 1,
                    archived = false,
                    activity = AgentSessionActivity.UNREAD,
                    localSessionIdentity = LocalSessionIdentity(path, LocalSessionId("air-pets-local")),
                    agentSessionId = "external-agent-session",
                )
                val view = requireNotNull(createAirQuickChat(project, requireNotNull(nativeChatTarget(summary)), lifetime, scope))
                Disposer.register(lifetime, view)
                screen = view.component.components.single() as JComponent
                forceRespectIsShowingClientProperty = true
                withShowingChanged {
                    ComponentUtil.forceMarkAsShowing(screen, true)
                    screen.dispatchEvent(HierarchyEvent(screen, HierarchyEvent.HIERARCHY_CHANGED, screen, null, HierarchyEvent.SHOWING_CHANGED.toLong()))
                }
                PlatformTestUtil.waitWithEventsDispatching("Native chat must subscribe to the existing session", { requests.isNotEmpty() }, 10)
                assertEquals("The external agent session ID is not AIR's runtime thread ID", runtimeId, requests.single().threadId)
                assertEquals(summary.workspaceId, requests.single().sessionWorkspaceId)
                assertEquals(path, requests.single().projectDirectory)
                PlatformTestUtil.waitWithEventsDispatching("The existing transcript must be rendered", {
                    screen.children().filterIsInstance<JComponent>().any { it.getClientProperty(THREAD_GUI_SEMANTIC_TEXT) == prompt }
                }, 10)
                assertTrue("AIR's snapshot must enable the native reply composer", requireNotNull(view.preferredFocusComponent).isEnabled)
            } finally {
                screen?.let { component ->
                    withShowingChanged {
                        ComponentUtil.forceMarkAsShowing(component, false)
                        component.dispatchEvent(HierarchyEvent(component, HierarchyEvent.HIERARCHY_CHANGED, component, null, HierarchyEvent.SHOWING_CHANGED.toLong()))
                    }
                }
                forceRespectIsShowingClientProperty = originalShowing
                scope.cancel()
                Disposer.dispose(lifetime)
            }
        }
    }

    private fun Container.children(): List<Component> = components.flatMap { listOf(it) + if (it is Container) it.children() else emptyList() }
}