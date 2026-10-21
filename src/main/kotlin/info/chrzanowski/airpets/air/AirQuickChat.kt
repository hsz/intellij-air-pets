package info.chrzanowski.airpets.air

import com.intellij.air.frontend.session.catalog.SessionSummary
import com.intellij.air.shared.core.AgentSessionAddress
import com.intellij.air.shared.core.thread.AgentThreadInteractionSurfaceId
import com.intellij.air.shared.core.thread.AgentThreadInteractionSurfaces
import com.intellij.air.shared.session.AgentThreadViewDeferredStartState
import com.intellij.air.thread.view.AgentThreadViewContent
import com.intellij.air.thread.view.AgentThreadViewContentContext
import com.intellij.air.thread.view.AgentThreadViewContentFile
import com.intellij.air.thread.view.AgentThreadViewContentHost
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
import kotlinx.coroutines.flow.flowOf
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.HierarchyEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal data class AirQuickChatTarget(val context: AgentThreadViewContentContext, val address: AgentSessionAddress)

internal fun nativeChatTarget(summary: SessionSummary): AirQuickChatTarget? {
    val surface = summary.interactionSurfaceId?.let { AgentThreadInteractionSurfaceId.fromOrNull(it) }
        ?: if (summary.interactionSurfaceId == null) AgentThreadInteractionSurfaces.NATIVE else return null
    if (surface != AgentThreadInteractionSurfaces.NATIVE) return null
    val directory = summary.localSessionIdentity.projectDirectory
    val id = summary.id
    if (directory.isBlank() || id.providerSessionId.isBlank()) return null
    val address = AgentSessionAddress.create(directory, id.agentId.value, id.launchTargetId, id.providerSessionId) ?: return null
    return AirQuickChatTarget(
        AgentThreadViewContentContext(
            agentId = id.agentId,
            interactionSurfaceId = surface,
            launchTargetId = id.launchTargetId,
            threadIdentity = address.sessionIdentity,
            threadId = id.providerSessionId,
            projectPath = summary.workspaceId.value,
            sessionWorkspaceId = summary.workspaceId,
            projectDirectory = directory,
            launchProfile = null,
            threadTitle = summary.title,
            subAgentId = null,
        ),
        address,
    )
}

internal fun createAirQuickChat(
    project: Project,
    target: AirQuickChatTarget,
    lifetime: Disposable,
    scope: CoroutineScope,
): QuickChatView? {
    val context = target.context
    val provider = AgentThreadViewContentProviders.find(context) ?: return null
    val shown = CompletableDeferred<Unit>()
    val panel = JPanel(BorderLayout())
    var disposed = false
    var content: AgentThreadViewContent? = null
    panel.addHierarchyListener { event ->
        if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && panel.isShowing) shown.complete(Unit)
    }
    val file = object : AgentThreadViewContentFile {
        override val topLevelSessionAddress = target.address
        override val topLevelSessionAddresses = flowOf(target.address)
        override val projectDirectory = context.projectDirectory
        override val threadIdentity = context.threadIdentity
        override val threadId = context.threadId
        override val launchProfile = context.launchProfile
        override val deferredStartStates = MutableStateFlow<AgentThreadViewDeferredStartState?>(null)
    }
    val host = object : AgentThreadViewContentHost {
        override val startupScope = scope
        override suspend fun awaitComponentShowing() = shown.await()
        override fun isDisposed() = disposed || Disposer.isDisposed(lifetime) || project.isDisposed
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