package info.chrzanowski.airpets

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import info.chrzanowski.airpets.air.AirSessionSource
import info.chrzanowski.airpets.air.createAirQuickChat
import info.chrzanowski.airpets.model.PetSession
import info.chrzanowski.airpets.model.PetState
import info.chrzanowski.airpets.model.SessionWorkingTime
import info.chrzanowski.airpets.model.demoSessions
import info.chrzanowski.airpets.ui.AirPetsStatusBarWidget
import info.chrzanowski.airpets.ui.AirPetsStatusBarWidgetFactory
import info.chrzanowski.airpets.ui.PetOverlay
import info.chrzanowski.airpets.ui.PetQuickChatPopup
import info.chrzanowski.airpets.ui.PetSprites
import info.chrzanowski.airpets.ui.addPetOverlay
import info.chrzanowski.airpets.ui.petMenuActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
class AirPetsService(private val project: Project, private val scope: CoroutineScope) : Disposable {
    private val options get() = project.service<AirPetsSettings>().state
    private val source = AirSessionSource()
    private var job: Job? = null
    private var overlay: PetOverlay? = null
    private var layer: JLayeredPane? = null
    private var liveSessions: List<PetSession> = emptyList()
    private var discoveryAvailable = false
    private val workingTime = SessionWorkingTime()
    private var demoData = demoSessions()
    private val markingDone = mutableSetOf<String>()
    private var message: String? = null
    private val quickChat = PetQuickChatPopup(
        project, scope,
        session = { id -> liveSessions.firstOrNull { it.id == id } },
        createView = { id, lifetime, chatScope ->
            source.quickChatTarget(project, id)?.let { createAirQuickChat(project, it, lifetime, chatScope) }
        },
        onOpenInAir = ::open,
        onSessionChanged = { overlay?.setQuickChatSession(it) },
    )

    @Volatile
    private var disposed = false
    var demo: Boolean = java.lang.Boolean.getBoolean("air.pets.demo")
        private set
    val enabled: Boolean get() = options.enabled
    val statusText: String
        get() = when {
            !enabled -> "Pets · off"
            demo -> "Pets · DEMO"
            message != null -> "Pets · !"
            else -> "Pets · ${liveSessions.size}"
        }
    val statusTooltip: String
        get() = buildString {
            append(if (demo) "Air Pets — DEMO (not live data)" else "Air Pets — ${liveSessions.size} live sessions")
            if (!enabled) append(" · pets hidden")
            if (!demo) message?.let { append(" · $it") }
            append(" · Click for preferences and all sessions")
        }

    private val resizeListener = object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) = positionOverlay()
    }

    @Synchronized
    fun start() {
        if (job != null || disposed || ApplicationManager.getApplication().isHeadlessEnvironment) return
        job = scope.launch {
            val sprites = withContext(Dispatchers.IO) { PetSprites.load() }
            withContext(Dispatchers.EDT) {
                if (!disposed) overlay = PetOverlay(sprites, ::open, ::markDone, ::showQuickChat, ::showMenu)
            }
            while (isActive && !disposed) {
                val attached = withContext(Dispatchers.EDT) { attach() }
                if (attached) break
                delay(500)
            }
            launch(Dispatchers.EDT) {
                while (isActive && !disposed) {
                    delay(1.seconds)
                    if (enabled && options.duration && !demo && liveSessions.any { it.state == PetState.WORKING }) refresh()
                }
            }
            while (isActive && !disposed) {
                try {
                    source.sessions(project).collect { sessions ->
                        withContext(Dispatchers.EDT) {
                            workingTime.observe(sessions)
                            liveSessions = sessions
                            discoveryAvailable = true
                            message = null
                            refresh()
                        }
                    }
                    break
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    logger<AirPetsService>().warn("Cannot observe Air sessions; retrying in 5 seconds", error)
                    withContext(Dispatchers.EDT) {
                        workingTime.observe(emptyList())
                        liveSessions = emptyList()
                        discoveryAvailable = false
                        message = "Air session connection unavailable. Retrying automatically; demo is still available."
                        refresh()
                    }
                    delay(5_000)
                }
            }
        }
    }

    fun setEnabled(value: Boolean) {
        options.enabled = value
        start()
        refresh()
    }

    fun setDemo(value: Boolean) {
        demo = value
        if (value) options.enabled = true
        start()
        refresh()
    }

    private fun attach(): Boolean {
        if (disposed || project.isDisposed) return false
        val component = WindowManager.getInstance().getIdeFrame(project)?.component ?: return false
        val target = SwingUtilities.getRootPane(component)?.layeredPane ?: return false
        val panel = overlay ?: return false
        layer = target
        target.addPetOverlay(panel)
        target.addComponentListener(resizeListener)
        refresh()
        logger<AirPetsService>().info("Air Pets overlay attached (demo=$demo)")
        return true
    }

    private fun positionOverlay() {
        overlay?.positionInParent()
    }

    private fun currentSessions() = if (demo) demoData else liveSessions.map { it.copy(observedWorkingMs = workingTime.elapsedMs(it.id)) }

    private fun refresh() {
        if (!enabled || demo) quickChat.close()
        quickChat.refresh(retainMissing = !discoveryAvailable)
        overlay?.let {
            it.isVisible = enabled
            it.update(currentSessions(), demo, options.labels, options.animated, options.cost, options.duration)
        }
        WindowManager.getInstance().getStatusBar(project)?.let { statusBar ->
            (statusBar.getWidget(AirPetsStatusBarWidgetFactory.ID) as? AirPetsStatusBarWidget)?.updatePresentation()
            statusBar.updateWidget(AirPetsStatusBarWidgetFactory.ID)
        }
        layer?.repaint()
    }

    private fun showQuickChat(id: String, anchor: Rectangle) {
        if (disposed || project.isDisposed || demo || !enabled) return
        val owner = overlay ?: return
        try {
            quickChat.open(id, owner, anchor)
        } catch (error: Exception) {
            logger<AirPetsService>().warn("Cannot show Air quick chat", error)
            message = "Could not open quick chat. Open the session from the Pets menu to continue in Air."
            refresh()
        }
    }

    private fun open(id: String) {
        if (demo) {
            demoData = demoData.map { session ->
                if (session.id != id) session else session.copy(
                    state = PetState.entries[(session.state.ordinal + 1) % PetState.entries.size],
                    detail = "Demo · click to cycle through states",
                )
            }
            refresh()
        } else if (!source.open(project, id)) {
            message = "This session is no longer available in Air."
            refresh()
        }
    }

    private fun markDone(id: String) {
        if (disposed || project.isDisposed || currentSessions().none { it.id == id && it.state == PetState.DONE }) return
        if (demo) {
            demoData = demoData.filterNot { it.id == id }
            refresh()
            return
        }
        if (!markingDone.add(id)) return
        scope.launch(Dispatchers.EDT) {
            try {
                if (!source.markDone(project, id) && !disposed && !project.isDisposed) {
                    message = "This session cannot be marked as done in Air right now."
                    refresh()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logger<AirPetsService>().warn("Cannot mark Air session as done", error)
                if (!disposed && !project.isDisposed) {
                    message = "Could not mark the session as done in Air. Please try again."
                    refresh()
                }
            } finally {
                markingDone.remove(id)
            }
        }
    }

    fun showStatusBarMenu(component: JComponent) {
        if (disposed || project.isDisposed) return
        val popup = createMenu(component)
        val size = popup.content.preferredSize
        popup.show(RelativePoint(component, Point(component.width - size.width, -size.height)))
    }

    private fun showMenu(component: JComponent, x: Int, y: Int) {
        if (disposed || project.isDisposed) return
        createMenu(component).show(RelativePoint(component, Point(x, y)))
    }

    private fun createMenu(component: JComponent) = JBPopupFactory.getInstance().createActionGroupPopup(
        "Air Pets",
        petMenuActions(
            options = options,
            demo = { demo },
            sessions = currentSessions(),
            message = message,
            onEnabled = ::setEnabled,
            onDemo = ::setDemo,
            onAnimated = { options.animated = it; refresh() },
            onLabels = { options.labels = it; refresh() },
            onCost = { options.cost = it; refresh() },
            onDuration = { options.duration = it; refresh() },
            onOpen = ::open,
        ),
        SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project, DataManager.getInstance().getDataContext(component)),
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true,
    )

    override fun dispose() {
        disposed = true
        job?.cancel()
        val cleanup = Runnable {
            quickChat.dispose()
            overlay?.dispose()
            layer?.removeComponentListener(resizeListener)
            overlay?.let { layer?.remove(it) }
            layer?.repaint()
            overlay = null
            layer = null
        }
        if (SwingUtilities.isEventDispatchThread()) cleanup.run()
        else ApplicationManager.getApplication().invokeLater(cleanup)
    }
}