package info.chrzanowski.airpets.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import info.chrzanowski.airpets.model.PetSession
import info.chrzanowski.airpets.model.PetState
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

internal class SessionHoverCard(
    onOpen: () -> Unit,
    onMarkDone: () -> Unit,
    onEnter: () -> Unit,
    onExit: () -> Unit,
) : JPanel(BorderLayout(0, JBUI.scale(8))) {
    private val title = text().apply { font = font.deriveFont(Font.BOLD) }
    private val state = label()
    private val workspace = text().apply { foreground = JBColor.GRAY }
    private val cost = label()
    private val duration = label()
    private val details = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val scroll = JBScrollPane(details, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
        border = JBUI.Borders.empty()
        isFocusable = false
    }
    private val open = JButton("Open session").apply {
        isFocusable = false
        addActionListener { onOpen() }
    }
    private val done = JButton("Mark as done").apply {
        isFocusable = false
        addActionListener { onMarkDone() }
    }

    init {
        isFocusable = false
        border = JBUI.Borders.empty(10, 12)
        listOf(title, state, workspace, cost, duration).forEach {
            it.alignmentX = Component.LEFT_ALIGNMENT
            details.add(it)
        }
        add(scroll, BorderLayout.CENTER)
        add(
            JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
                add(open)
                add(done)
            },
            BorderLayout.SOUTH,
        )
        val listener = object : MouseAdapter() {
            override fun mouseEntered(event: MouseEvent) = onEnter()
            override fun mouseExited(event: MouseEvent) = onExit()
        }

        fun listen(component: Component) {
            component.addMouseListener(listener)
            if (component is java.awt.Container) component.components.forEach(::listen)
        }
        listen(this)
    }

    fun update(
        session: PetSession,
        demo: Boolean,
        labels: Boolean,
        showCost: Boolean,
        showDuration: Boolean,
        width: Int,
        maxDetailsHeight: Int = JBUI.scale(260),
    ) {
        val contentWidth = (width - insets.left - insets.right).coerceAtLeast(JBUI.scale(80))
        val textWidth = (contentWidth - scroll.verticalScrollBar.preferredSize.width).coerceAtLeast(JBUI.scale(60))
        title.setWrappedText(if (labels) session.title.ifBlank { "Untitled session" } else "Air session", textWidth)
        state.text = "${session.state.label}${if (demo) " · Demo (not live data)" else ""}"
        workspace.setWrappedText(
            listOfNotNull(
                session.workspacePath?.takeIf { it.isNotBlank() }?.let { "Workspace: $it" },
                session.worktreeBranch?.takeIf { it.isNotBlank() }?.let { "Worktree branch: $it" },
            ).joinToString("\n"),
            textWidth,
        )
        workspace.isVisible = workspace.text.isNotEmpty()
        cost.isVisible = showCost
        cost.text = if (session.cost?.estimated == true) "Estimated cost (USD): " else "Cost (USD): "
        cost.text += sessionCostText(session.cost) ?: "Not reported by Air"
        duration.isVisible = showDuration
        duration.text = "Observed working time: ${sessionDurationText(session.observedWorkingMs) ?: "Not yet observed"}"
        duration.toolTipText = "This project opening only; not Air's historical session duration"
        open.text = if (demo) "Change demo state" else "Open session"
        done.isVisible = session.state == PetState.DONE
        done.toolTipText = if (demo) "Remove this demo session" else "Mark as done in Air (archives this session)"
        scroll.preferredSize = Dimension(contentWidth, minOf(details.preferredSize.height, maxDetailsHeight))
        revalidate()
        repaint()
    }

    private fun text() = JTextArea().apply {
        isEditable = false
        isFocusable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        font = JBUI.Fonts.label()
        border = JBUI.Borders.emptyBottom(6)
    }

    private fun label() = JLabel().apply {
        putClientProperty("html.disable", true)
        border = JBUI.Borders.emptyBottom(6)
    }

    private fun JTextArea.setWrappedText(value: String, width: Int) {
        text = value
        preferredSize = null
        setSize(width, Short.MAX_VALUE.toInt())
        preferredSize = Dimension(width, preferredSize.height)
    }
}

internal fun hoverCardLocation(anchor: Rectangle, size: Dimension, area: Rectangle): Point {
    val gap = JBUI.scale(6)
    val above = anchor.y - size.height - gap
    val below = anchor.y + anchor.height + gap
    fun x(value: Int) = value.coerceIn(area.x, (area.x + area.width - size.width).coerceAtLeast(area.x))
    fun y(value: Int) = value.coerceIn(area.y, (area.y + area.height - size.height).coerceAtLeast(area.y))
    val centeredX = x(anchor.x + anchor.width / 2 - size.width / 2)
    val centeredY = y(anchor.y + anchor.height / 2 - size.height / 2)
    return listOf(
        Point(centeredX, above), Point(centeredX, below),
        Point(anchor.x + anchor.width + gap, centeredY), Point(anchor.x - size.width - gap, centeredY),
    ).firstOrNull { area.contains(Rectangle(it, size)) } ?: Point(centeredX, y(above))
}