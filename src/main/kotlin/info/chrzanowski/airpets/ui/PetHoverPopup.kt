package info.chrzanowski.airpets.ui

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import info.chrzanowski.airpets.model.PetSession
import info.chrzanowski.airpets.model.PetState
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal data class HoverCardData(
    val session: PetSession,
    val anchor: Rectangle,
    val demo: Boolean,
    val labels: Boolean,
    val cost: Boolean,
    val duration: Boolean,
)

internal fun createHoverPopup(card: SessionHoverCard, onClosed: () -> Unit): JBPopup = JBPopupFactory.getInstance()
    .createComponentPopupBuilder(card, null)
    .setRequestFocus(false)
    .setFocusable(false)
    .setMovable(false)
    .setResizable(false)
    .setCancelOnClickOutside(true)
    .setCancelOnOtherWindowOpen(true)
    .setCancelOnWindowDeactivation(true)
    .setCancelKeyEnabled(true)
    .setShowShadow(true)
    .addListener(
        object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) = onClosed()
        },
    )
    .createPopup()

internal class PetHoverPopup(
    private val owner: JComponent,
    private val data: (String) -> HoverCardData?,
    private val onOpen: (String) -> Unit,
    private val onMarkDone: (String) -> Unit,
    later: (Int, () -> Unit) -> (() -> Unit) = ::scheduleHover,
    private val showPopup: (JBPopup, RelativePoint) -> Unit = { popup, point -> popup.show(point) },
) {
    private var popup: JBPopup? = null
    private var card: SessionHoverCard? = null
    private val lifecycle = HoverCardLifecycle(::show, ::close, later)
    val sessionId: String? get() = lifecycle.sessionId

    fun hover(id: String?) = lifecycle.hover(id)
    fun cancel() = lifecycle.cancel()
    fun isOpenFor(id: String) = lifecycle.visible && sessionId == id

    fun refresh() {
        val id = sessionId ?: return
        val current = data(id)
        if (current == null || !owner.isShowing) cancel()
        else card?.let { content ->
            update(content, current)
            popup?.let {
                it.pack(true, true)
                val point = location(current, it)
                SwingUtilities.convertPointToScreen(point, owner)
                it.setLocation(point)
            }
        }
    }

    private fun show(id: String) {
        val current = data(id)
        if (current == null || !owner.isShowing) {
            cancel()
            return
        }
        val content = SessionHoverCard(
            onOpen = { act(id, false) },
            onMarkDone = { act(id, true) },
            onEnter = lifecycle::enterCard,
            onExit = lifecycle::leaveCard,
        )
        update(content, current)
        card = content
        val created = createHoverPopup(content) {
            if (card === content) {
                popup = null
                card = null
                lifecycle.cancel()
                owner.repaint()
            }
        }
        popup = created
        showPopup(created, RelativePoint(owner, location(current, created)))
        owner.repaint()
    }

    private fun act(id: String, markDone: Boolean) {
        val current = data(id)
        cancel()
        if (current != null) {
            if (markDone) {
                if (current.session.state == PetState.DONE) onMarkDone(id)
            } else onOpen(id)
        }
    }

    private fun update(content: SessionHoverCard, current: HoverCardData) = content.update(
        current.session, current.demo, current.labels, current.cost, current.duration,
        minOf(JBUI.scale(320), (owner.width - JBUI.scale(24)).coerceAtLeast(JBUI.scale(200))),
        minOf(JBUI.scale(260), (owner.height - JBUI.scale(160)).coerceAtLeast(JBUI.scale(80))),
    )

    private fun location(current: HoverCardData, popup: JBPopup) = hoverCardLocation(
        current.anchor, popup.content.preferredSize, Rectangle(0, 0, owner.width, owner.height),
    )

    private fun close() {
        val previous = popup
        popup = null
        card = null
        previous?.cancel()
        owner.repaint()
    }
}