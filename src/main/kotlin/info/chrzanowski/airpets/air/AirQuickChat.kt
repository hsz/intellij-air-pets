package info.chrzanowski.airpets.air

import com.intellij.air.frontend.session.catalog.SessionSummary
import com.intellij.air.shared.core.AgentSessionAddress
import com.intellij.air.shared.core.thread.AgentThreadInteractionSurfaceId
import com.intellij.air.shared.core.thread.AgentThreadInteractionSurfaces
import com.intellij.air.shared.core.thread.terminalAgentLaunchTargetId
import com.intellij.air.shared.prompt.AgentPromptLaunchProfile
import com.intellij.air.shared.session.AgentThreadViewDeferredStartState
import com.intellij.air.thread.view.AgentThreadViewContent
import com.intellij.air.thread.view.AgentThreadViewContentContext
import com.intellij.air.thread.view.AgentThreadViewContentFile
import com.intellij.air.thread.view.AgentThreadViewContentHost
import com.intellij.air.thread.view.AgentThreadViewContentIdentity
import com.intellij.air.thread.view.AgentThreadViewContentProviders
import com.intellij.air.thread.view.AgentThreadViewContentRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import info.chrzanowski.airpets.ui.QuickChatView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.HierarchyEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal data class AirQuickChatTarget(val context: AgentThreadViewContentContext, val address: AgentSessionAddress)

private val interactionSurfaceGetter by lazy {
    val getters = SessionSummary::class.java.methods.filter { it.parameterCount == 0 && it.returnType == String::class.java }
    getters.firstOrNull { it.name == "getInteractionSurfaceId" }
        ?: getters.firstOrNull { it.name.startsWith("getEffectiveInteractionSurfaceId-") }
}

private val identityContextConstructor by lazy {
    AgentThreadViewContentContext::class.java.constructors.firstOrNull {
        it.parameterTypes.contentEquals(arrayOf(AgentThreadViewContentIdentity::class.java, AgentPromptLaunchProfile::class.java, String::class.java))
    }
}

internal fun nativeChatTarget(summary: SessionSummary): AirQuickChatTarget? {
    val getter = interactionSurfaceGetter ?: return null
    val id = summary.id
    val surfaceId = getter.invoke(summary) as String?
    val surface = surfaceId?.let { AgentThreadInteractionSurfaceId.fromOrNull(it) }
        ?: if (surfaceId == null && id.launchTargetId != terminalAgentLaunchTargetId(id.agentId)) AgentThreadInteractionSurfaces.NATIVE else return null
    if (surface != AgentThreadInteractionSurfaces.NATIVE) return null
    val directory = summary.localSessionIdentity.projectDirectory
    if (directory.isBlank() || id.providerSessionId.isBlank()) return null
    val address = AgentSessionAddress.create(directory, id.agentId.value, id.launchTargetId, id.providerSessionId) ?: return null
    val identity = AgentThreadViewContentIdentity(
        agentId = id.agentId,
        interactionSurfaceId = surface,
        launchTargetId = id.launchTargetId,
        threadIdentity = address.sessionIdentity,
        threadId = id.providerSessionId,
        projectPath = summary.workspaceId.value,
        sessionWorkspaceId = summary.workspaceId,
        projectDirectory = directory,
        subAgentId = null,
    )
    val context = identityContextConstructor?.newInstance(identity, null, summary.title) as AgentThreadViewContentContext?
        ?: AgentThreadViewContentContext(
            agentId = identity.agentId,
            interactionSurfaceId = identity.interactionSurfaceId,
            launchTargetId = identity.launchTargetId,
            threadIdentity = identity.threadIdentity,
            threadId = identity.threadId,
            projectPath = identity.projectPath,
            sessionWorkspaceId = identity.sessionWorkspaceId,
            projectDirectory = identity.projectDirectory,
            launchProfile = null,
            threadTitle = summary.title,
            subAgentId = identity.subAgentId,
        )
    return AirQuickChatTarget(context, address)
}

internal fun createAirQuickChat(
    project: Project,
    target: AirQuickChatTarget,
    lifetime: Disposable,
    scope: CoroutineScope,
): QuickChatView? {
    val context = target.context
    val provider = AgentThreadViewContentProviders.find(context) ?: return null
    val lifetimeState = Disposer.newCheckedDisposable()
    if (!Disposer.tryRegister(lifetime, lifetimeState)) {
        Disposer.dispose(lifetimeState)
        return null
    }
    val shown = CompletableDeferred<Unit>()
    val panel = JPanel(BorderLayout())
    var disposed = false
    var content: AgentThreadViewContent? = null
    panel.addHierarchyListener { event ->
        if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && panel.isShowing) shown.complete(Unit)
    }
    val file = object : AgentThreadViewContentFile {
        override val topLevelSessionAddress = target.address
        override val topLevelSessionAddresses: StateFlow<AgentSessionAddress> = MutableStateFlow(target.address)
        override val projectDirectory = context.identity.projectDirectory
        override val threadIdentity = context.identity.threadIdentity
        override val threadId = context.identity.threadId
        override val launchProfile = context.launchProfile
        override val deferredStartStates = MutableStateFlow<AgentThreadViewDeferredStartState?>(null)
    }
    val host = object : AgentThreadViewContentHost {
        override val startupScope = scope
        override suspend fun awaitComponentShowing() = shown.await()
        override fun isDisposed() = disposed || lifetimeState.isDisposed || project.isDisposed
        override fun refreshForFileStateChange() { content?.refreshForFileStateChange() }
        override fun handleRestoreValidationError(validationError: String) {
            panel.removeAll()
            panel.add(JLabel("This conversation could not be restored. Open it in Air to continue.").apply { border = JBUI.Borders.empty(16) })
            panel.revalidate()
            panel.repaint()
        }
        override fun updateThreadTitle(threadTitle: String) = Unit
        override suspend fun openSubagent(subagentId: String, title: String) = false
        override suspend fun openRootThread(title: String) = false
        override fun showSessionMenu(component: Component, x: Int, y: Int) = Unit
    }
    content = provider.createContent(AgentThreadViewContentRequest(project, context, lifetime, file, host))
    content.start { installed ->
        val component = installed.component
        if (host.isDisposed() || component == null) false
        else {
            content = installed
            panel.removeAll()
            panel.add(component, BorderLayout.CENTER)
            panel.revalidate()
            panel.repaint()
            true
        }
    }
    return object : QuickChatView {
        override val component: JComponent = panel
        override val preferredFocusComponent: JComponent? get() = content.preferredFocusedComponent
        override fun shown() = content.selectNotify()
        override fun hidden() = content.deselectNotify()
        override fun dispose() {
            content.deselectNotify()
            disposed = true
            shown.cancel()
        }
    }
}