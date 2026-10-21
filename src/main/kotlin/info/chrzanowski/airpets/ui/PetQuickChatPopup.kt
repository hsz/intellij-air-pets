package info.chrzanowski.airpets.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import info.chrzanowski.airpets.model.PetSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

internal interface QuickChatView : Disposable {
    val component: JComponent
    val preferredFocusComponent: JComponent?
    fun shown() = Unit
    fun hidden() = Unit
}

internal class PetQuickChatPopup(
    private val project: Project,
    private val scope: CoroutineScope,
    private val session: (String) -> PetSession?,
    private val createView: suspend (String, Disposable, CoroutineScope) -> QuickChatView?,
    private val onOpenInAir: (String) -> Unit,
    private val onSessionChanged: (String?) -> Unit,
    private val showPopup: (JBPopup, RelativePoint) -> Unit = { popup, point -> popup.show(point) },
) : Disposable {
    private class CachedView(val lifetime: Disposable, val job: Job) {
        var view: QuickChatView? = null
    }

    private class Entry(
        val id: String,
        val owner: JComponent,
        val popup: JBPopup,
        val cached: CachedView,
        val conversation: JPanel,
    ) {
        var focus: JComponent? = null
    }

    private val cachedViews = mutableMapOf<String, CachedView>()
    private var active: Entry? = null
    private var disposed = false
    val sessionId: String? get() = active?.id

    fun open(id: String, owner: JComponent, anchor: Rectangle) {
        if (disposed || project.isDisposed || !owner.isShowing) return
        val current = session(id) ?: return
        active?.takeIf { it.id == id }?.let {
            it.popup.setCaption(current.title.ifBlank { "Untitled session" })
            IdeFocusManager.getInstance(project).requestFocus(it.focus ?: it.popup.content, true)
            return
        }
        close()
        val cached = cachedViews.getOrPut(id) {
            CachedView(Disposer.newDisposable("Air Pets quick chat"), SupervisorJob(scope.coroutineContext[Job]))
        }
        val viewScope = CoroutineScope(scope.coroutineContext + cached.job + Dispatchers.EDT)
        val content = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(
                minOf(JBUI.scale(480), owner.width).coerceAtLeast(1),
                minOf(JBUI.scale(540), owner.height).coerceAtLeast(1),
            )
        }
        val conversation = JPanel(BorderLayout()).apply {
            add(message("Loading Air conversation…"), BorderLayout.CENTER)
        }
        val open = JButton("Open in Air").apply {
            addActionListener {
                if (active?.id == id && session(id) != null) {
                    close()
                    onOpenInAir(id)
                }
            }
        }
        content.add(conversation, BorderLayout.CENTER)
        content.add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply { add(open) }, BorderLayout.SOUTH)
        lateinit var entry: Entry
        val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, open)
            .setProject(project)
            .setTitle(current.title.ifBlank { "Untitled session" })
            .setRequestFocus(true)
            .setFocusable(true)
            .setResizable(true)
            .setMovable(true)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(false)
            .setCancelOnWindowDeactivation(false)
            .setCancelKeyEnabled(true)
            .setMinSize(Dimension(minOf(JBUI.scale(320), content.preferredSize.width), minOf(JBUI.scale(240), content.preferredSize.height)))
            .addListener(object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) {
                    if (active === entry) close()
                }
            })
            .createPopup()
        entry = Entry(id, owner, popup, cached, conversation)
        active = entry
        onSessionChanged(id)
        try {
            val decorated = popup.content.preferredSize
            content.preferredSize = Dimension(
                (content.preferredSize.width - (decorated.width - owner.width).coerceAtLeast(0)).coerceAtLeast(1),
                (content.preferredSize.height - (decorated.height - owner.height).coerceAtLeast(0)).coerceAtLeast(1),
            )
            val point = hoverCardLocation(anchor, popup.content.preferredSize, Rectangle(0, 0, owner.width, owner.height))
            showPopup(popup, RelativePoint(owner, point))
            cached.view?.let {
                mount(entry, it)
                return
            }
            viewScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    val view = createView(id, cached.lifetime, viewScope)
                    if (cachedViews[id] !== cached) {
                        view?.let(Disposer::dispose)
                        return@launch
                    }
                    if (view == null) {
                        conversation.removeAll()
                        conversation.add(message("Quick chat is unavailable for this session. Open it in Air to continue."))
                    } else {
                        cached.view = view
                        Disposer.register(cached.lifetime, view)
                        if (active === entry) mount(entry, view)
                    }
                    conversation.revalidate()
                    conversation.repaint()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    logger<PetQuickChatPopup>().warn("Cannot open Air quick chat", error)
                    if (active === entry) {
                        conversation.removeAll()
                        conversation.add(message("Could not load this conversation. Open it in Air to continue."))
                        conversation.revalidate()
                        conversation.repaint()
                    }
                }
            }
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    private fun mount(entry: Entry, view: QuickChatView) {
        entry.conversation.removeAll()
        entry.conversation.add(view.component, BorderLayout.CENTER)
        view.shown()
        entry.focus = view.preferredFocusComponent
        entry.conversation.revalidate()
        entry.conversation.repaint()
        entry.focus?.let { focus ->
            IdeFocusManager.getInstance(project).doWhenFocusSettlesDown {
                if (active === entry && focus.isShowing) IdeFocusManager.getInstance(project).requestFocus(focus, true)
            }
        }
    }

    fun refresh(retainMissing: Boolean = false) {
        active?.let {
            val current = if (project.isDisposed) null else session(it.id)
            if (current == null || !it.owner.isShowing) close()
            else it.popup.setCaption(current.title.ifBlank { "Untitled session" })
        }
        cachedViews.keys.toList().filter { project.isDisposed || (!retainMissing && session(it) == null) }.forEach(::release)
    }

    fun close() {
        val previous = active ?: return
        active = null
        onSessionChanged(null)
        previous.cached.view?.hidden()
        previous.conversation.removeAll()
        previous.popup.cancel()
        if (!previous.popup.isDisposed) Disposer.dispose(previous.popup)
        if (previous.cached.view == null) release(previous.id)
    }

    private fun release(id: String) {
        cachedViews.remove(id)?.let {
            it.job.cancel()
            Disposer.dispose(it.lifetime)
        }
    }

    override fun dispose() {
        disposed = true
        close()
        cachedViews.keys.toList().forEach(::release)
    }

    private fun message(text: String) = JTextArea(text).apply {
        isEditable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        font = JBUI.Fonts.label()
        border = JBUI.Borders.empty(16)
    }
}