package info.chrzanowski.airpets.ui

import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import info.chrzanowski.airpets.model.PetKind
import info.chrzanowski.airpets.model.PetMotion
import info.chrzanowski.airpets.model.PetSession
import info.chrzanowski.airpets.model.PetState
import info.chrzanowski.airpets.model.visibleSessions
import java.awt.Color
import java.awt.Cursor
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.roundToInt

internal fun JLayeredPane.addPetOverlay(overlay: PetOverlay) {
    add(overlay)
    setLayer(overlay, JLayeredPane.PALETTE_LAYER)
    overlay.positionInParent()
}

class PetOverlay(
    private val sprites: PetSprites,
    private val onOpen: (String) -> Unit,
    private val onMarkDone: (String) -> Unit = {},
    private val onMenu: (JComponent, Int, Int) -> Unit,
) : JComponent() {
    private var sessions: List<PetSession> = emptyList()
    private val motions = mutableMapOf<String, PetMotion>()
    private val manualPositions = mutableMapOf<String, ManualPosition>()
    private var drag: Drag? = null
    private var pointer: Point? = null
    private var hoveredId: String? = null
    private var demo = false
    private var labels = true
    private var animated = true
    private var cost = false
    private var duration = false
    private var lastTick = 0L
    private var disposed = false
    private val timer = Timer(100) { tick() }
    private val hover = PetHoverPopup(this, { id ->
        placements().firstOrNull { it.session.id == id }?.let {
            HoverCardData(it.session, it.body.union(it.badge), demo, labels, cost, duration)
        }
    }, onOpen, onMarkDone)
    private val scaleListener = PropertyChangeListener {
        SwingUtilities.invokeLater {
            if (!disposed) {
                hover.cancel()
                font = JBUI.Fonts.smallFont()
                positionInParent()
                repaint()
            }
        }
    }

    val overlayHeight: Int get() = JBUI.scale(if (cost || duration) 167 else 148)

    init {
        isOpaque = false
        isFocusable = false
        font = JBUI.Fonts.smallFont()
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        JBUIScale.addUserScaleChangeListener(scaleListener)
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) = hover.cancel()
            override fun componentHidden(event: ComponentEvent) = hover.cancel()
        })
        addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
                if (!isShowing) hover.cancel()
                updateTimer()
            }
        }
        val mouseListener = object : MouseAdapter() {
            override fun mouseEntered(event: MouseEvent) = mouseMoved(event)

            override fun mouseMoved(event: MouseEvent) {
                hoveredId = drag?.takeIf { it.started }?.id ?: petAt(event.point)?.session?.id
                pointer = event.point
                if (drag == null) hover.hover(hoveredId)
                repaint()
            }

            override fun mouseDragged(event: MouseEvent) {
                val active = drag
                if (active != null && event.modifiersEx and InputEvent.BUTTON1_DOWN_MASK != 0) {
                    if (active.started || active.origin.distance(event.point) >= JBUI.scale(5)) {
                        hover.cancel()
                        active.started = true
                        val scale = JBUIScale.scale(1.0f).toDouble()
                        manualPositions[active.id] = active.position.copy(
                            x = active.position.x + (event.x - active.origin.x) / scale,
                            y = active.position.y + (event.y - active.origin.y) / scale,
                        )
                        placements().firstOrNull { it.session.id == active.id }?.let {
                            manualPositions[active.id] = it.manualPosition()
                        }
                        cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                    }
                }
                mouseMoved(event)
            }

            override fun mouseExited(event: MouseEvent) {
                pointer = null
                hoveredId = null
                hover.hover(null)
                repaint()
            }

            override fun mousePressed(event: MouseEvent) {
                drag = null
                mouseMoved(event)
                hover.cancel()
                if (event.isPopupTrigger) onMenu(this@PetOverlay, event.x, event.y)
                else if (event.button == MouseEvent.BUTTON1) {
                    petAt(event.point)?.let { drag = Drag(it.session.id, event.point, it.manualPosition()) }
                }
            }

            override fun mouseReleased(event: MouseEvent) {
                val wasDragging = drag?.started == true
                drag = null
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                mouseMoved(event)
                hover.cancel()
                when {
                    event.isPopupTrigger -> onMenu(this@PetOverlay, event.x, event.y)
                    event.button == MouseEvent.BUTTON1 && !wasDragging -> {
                        val pet = petAt(event.point)
                        if (pet != null) {
                            if (pet.canMarkDoneAt(event.point)) onMarkDone(pet.session.id)
                            else onOpen(pet.session.id)
                        }
                    }
                }
                repaint()
            }
        }
        addMouseListener(mouseListener)
        addMouseMotionListener(mouseListener)
    }

    internal fun positionInParent() {
        val target = parent ?: return
        setBounds(0, 0, target.width, (target.height - JBUI.scale(28)).coerceAtLeast(0))
    }

    fun update(
        sessions: List<PetSession>,
        demo: Boolean,
        labels: Boolean,
        animated: Boolean,
        cost: Boolean = false,
        duration: Boolean = false,
    ) {
        if (this.demo != demo) hover.cancel()
        this.sessions = sessions
        this.demo = demo
        this.labels = labels
        this.animated = animated
        this.cost = cost
        this.duration = duration
        motions.keys.retainAll(sessions.map { it.id }.toSet())
        sessions.forEach { motions.getOrPut(it.id) { PetMotion(it.id) } }
        hover.refresh()
        updateTimer()
        repaint()
    }

    override fun addNotify() {
        super.addNotify()
        updateTimer()
    }

    override fun removeNotify() {
        timer.stop()
        hover.cancel()
        drag = null
        pointer = null
        hoveredId = null
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        super.removeNotify()
    }

    fun dispose() {
        disposed = true
        timer.stop()
        hover.cancel()
        JBUIScale.removeUserScaleChangeListener(scaleListener)
        motions.clear()
        manualPositions.clear()
        drag = null
    }

    private fun updateTimer() {
        if (!disposed && isShowing && animated && sessions.isNotEmpty()) {
            lastTick = System.nanoTime()
            timer.start()
        } else timer.stop()
    }

    private fun tick() {
        if (!isShowing) {
            timer.stop()
            return
        }
        val now = System.nanoTime()
        val delta = (now - lastTick) / 1_000_000_000.0
        lastTick = now
        placements().forEach { placement ->
            if (placement.session.id != hoveredId && placement.session.id != hover.sessionId) {
                motions.getValue(placement.session.id).advance(delta, placement.travel.toDouble(), placement.session.state)
            }
        }
        repaint()
    }

    private fun placements(): List<Placement> {
        val available = (width - JBUI.scale(24)).coerceAtLeast(0)
        val capacity = (available / JBUI.scale(148)).coerceIn(1, 12)
        val visible = visibleSessions(sessions, capacity)
        if (visible.isEmpty()) return emptyList()
        val lane = available / visible.size
        val size = minOf(JBUI.scale(64), lane).coerceAtLeast(1)
        return visible.mapIndexed { index, session ->
            val motion = motions.getValue(session.id)
            val manual = manualPositions[session.id]
            val scale = JBUIScale.scale(1.0f).toDouble()
            val travel = (lane - size - JBUI.scale(20)).coerceAtLeast(0)
            val x = manual?.let { (it.x * scale).roundToInt() }
                ?: (JBUI.scale(12) + index * lane + JBUI.scale(10) + (motion.position * travel).toInt())
            val bounce = if (animated && manual == null) JBUI.scale(motion.bounce(session.state)) else 0
            val y = manual?.let { (it.y * scale).roundToInt() }
                ?: ((height - overlayHeight).coerceAtLeast(0) + JBUI.scale(52) - bounce)
            val body = Rectangle(x, y, size, size)
            val metrics = getFontMetrics(font)
            val name = fit(metrics, session.title, minOf(lane - JBUI.scale(14), JBUI.scale(230)))
            val readoutText = sessionReadout(session, cost, duration)
            val readoutWidth = (metrics.stringWidth(readoutText) + JBUI.scale(10))
                .coerceAtMost(minOf(lane - JBUI.scale(14), JBUI.scale(230)).coerceAtLeast(1))
            val readout = readoutText.takeIf { it.isNotEmpty() }?.let {
                Rectangle((body.centerX - readoutWidth / 2.0).roundToInt(), body.y + size + JBUI.scale(1), readoutWidth, JBUI.scale(18))
            }
            val labelWidth = metrics.stringWidth(name) + JBUI.scale(10)
            val labelX = (body.centerX - labelWidth / 2.0).roundToInt()
            val labelY = body.y + size + JBUI.scale(1) + if (readout != null) JBUI.scale(19) else 0
            val label = Rectangle(labelX, labelY, labelWidth, JBUI.scale(18))
            val badge = Rectangle(x + size - JBUI.scale(13), body.y - JBUI.scale(20), JBUI.scale(28), JBUI.scale(22))
            Placement(session, body, label, badge, travel, readoutText, readout).apply { keepInside() }
        }
    }

    private fun petAt(point: Point) = placements().lastOrNull { it.contains(point) }

    override fun contains(x: Int, y: Int): Boolean = super.contains(x, y) && placements().any { it.contains(Point(x, y)) }

    override fun getToolTipText(event: MouseEvent): String? = null

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g.font = font
            placements().forEach { paintPet(g, it) }
        } finally {
            g.dispose()
        }
    }

    private fun paintPet(g: Graphics2D, placement: Placement) {
        val (session, body, label, badge) = placement
        val motion = motions.getValue(session.id)
        val kind = PetKind.forSession(session.id)
        val frame = sprites.frame(kind, session.state, if (animated) motion.elapsedSeconds else 0.0)
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            if (kind.pixelArt) RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR else RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        val flip = !motion.facingRight
        val ratio = minOf(body.width.toDouble() / frame.width, body.height.toDouble() / frame.height)
        val imageWidth = (frame.width * ratio).toInt().coerceAtLeast(1)
        val imageHeight = (frame.height * ratio).toInt().coerceAtLeast(1)
        val imageX = body.x + (body.width - imageWidth) / 2
        val imageY = body.y + body.height - imageHeight
        g.drawImage(frame, if (flip) imageX + imageWidth else imageX, imageY, if (flip) -imageWidth else imageWidth, imageHeight, null)
        pill(g, badge, stateColor(session.state))
        g.color = Color.WHITE
        val metrics = g.fontMetrics
        val symbol = if (pointer?.let(placement::canMarkDoneAt) == true) "✓" else session.state.badge
        g.drawString(symbol, badge.x + (badge.width - metrics.stringWidth(symbol)) / 2, badge.y + JBUI.scale(15))
        placement.readout?.let { bounds ->
            pill(g, bounds, backgroundColor())
            g.color = JBColor.foreground()
            g.drawString(fit(g, placement.readoutText, bounds.width - JBUI.scale(10)), bounds.x + JBUI.scale(5), bounds.y + JBUI.scale(13))
        }
        if (placement.labelVisible()) {
            val name = fit(g, session.title, label.width - JBUI.scale(10))
            pill(g, label, backgroundColor())
            g.color = JBColor.foreground()
            g.drawString(name, label.x + JBUI.scale(5), label.y + JBUI.scale(13))
        }
    }

    private fun backgroundColor() = JBColor(Color(246, 247, 249, 240), Color(43, 45, 49, 240))

    private fun stateColor(state: PetState): Color = when (state) {
        PetState.WAITING -> Color(167, 103, 20)
        PetState.ERROR -> Color(190, 65, 79)
        PetState.WORKING -> Color(58, 110, 190)
        PetState.REVIEW -> Color(115, 83, 177)
        PetState.DONE -> Color(44, 133, 96)
        PetState.RESTING -> Color(100, 111, 125)
    }

    private fun pill(g: Graphics2D, bounds: Rectangle, color: Color) {
        g.color = color
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, JBUI.scale(12), JBUI.scale(12))
    }

    private fun fit(g: Graphics2D, text: String, maxWidth: Int): String = fit(g.fontMetrics, text, maxWidth)

    private fun fit(metrics: FontMetrics, text: String, maxWidth: Int): String {
        if (metrics.stringWidth(text) <= maxWidth) return text
        var result = text.take(80)
        while (result.isNotEmpty() && metrics.stringWidth("$result…") > maxWidth) result = result.dropLast(1)
        return "$result…"
    }

    private data class ManualPosition(val x: Double, val y: Double)

    private data class Drag(val id: String, val origin: Point, val position: ManualPosition, var started: Boolean = false)

    private inner class Placement(
        val session: PetSession,
        val body: Rectangle,
        val label: Rectangle,
        val badge: Rectangle,
        val travel: Int,
        val readoutText: String,
        val readout: Rectangle?,
    ) {
        operator fun component1() = session
        operator fun component2() = body
        operator fun component3() = label
        operator fun component4() = badge
        fun canMarkDoneAt(point: Point) = session.state == PetState.DONE && badge.contains(point)
        fun contains(point: Point) = body.contains(point) || badge.contains(point) || readout?.contains(point) == true ||
            (labelVisible() && labelContains(point))

        fun labelVisible() = labels && !hover.isOpenFor(session.id) && hoveredId == session.id &&
            (drag?.id == session.id || pointer?.let { body.contains(it) || badge.contains(it) || readout?.contains(it) == true || labelContains(it) } == true)

        private fun labelContains(point: Point) = Rectangle(
            label.x, body.y + body.height, label.width, label.y + label.height - body.y - body.height,
        ).contains(point)

        fun manualPosition(): ManualPosition {
            val scale = JBUIScale.scale(1.0f).toDouble()
            return ManualPosition(body.x / scale, body.y / scale)
        }

        fun keepInside() {
            val bounds = body.union(badge).apply {
                readout?.let(::add)
                if (labels) add(Rectangle(body.x, label.y, body.width, label.height))
            }
            val dx = bounds.x.coerceIn(0, (width - bounds.width).coerceAtLeast(0)) - bounds.x
            val dy = bounds.y.coerceIn(0, (height - bounds.height).coerceAtLeast(0)) - bounds.y
            body.translate(dx, dy)
            badge.translate(dx, dy)
            label.translate(dx, dy)
            readout?.translate(dx, dy)
            if (bounds.width > width || bounds.height > height) {
                val petBounds = body.union(badge)
                val petDx = petBounds.x.coerceIn(0, (width - petBounds.width).coerceAtLeast(0)) - petBounds.x
                val petDy = petBounds.y.coerceIn(0, (height - petBounds.height).coerceAtLeast(0)) - petBounds.y
                body.translate(petDx, petDy)
                badge.translate(petDx, petDy)
                label.y = label.y.coerceIn(0, (height - label.height).coerceAtLeast(0))
                readout?.let { it.y = it.y.coerceIn(0, (height - it.height).coerceAtLeast(0)) }
            }
            label.x = label.x.coerceIn(0, (width - label.width).coerceAtLeast(0))
            readout?.let { it.x = it.x.coerceIn(0, (width - it.width).coerceAtLeast(0)) }
        }
    }
}