package info.chrzanowski.airpets.ui

import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import info.chrzanowski.airpets.model.PetSession
import info.chrzanowski.airpets.model.PetState
import info.chrzanowski.airpets.model.SessionCost
import info.chrzanowski.airpets.model.demoSessions
import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.Cursor
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.math.BigDecimal
import javax.imageio.ImageIO
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.SwingUtilities

class PetOverlayTest {
    @Test
    fun `enabled metrics render beside all demo pets at both zoom levels`() {
        val sprites = PetSprites.load()
        val originalScale = JBUIScale.scale(1.0f)
        try {
            for (scale in listOf(1.0f, 2.0f)) {
                SwingUtilities.invokeAndWait {
                    JBUIScale.setUserScaleFactorForTest(scale)
                    val panel = PetOverlay(sprites, {}) { _, _, _ -> }
                    try {
                        panel.update(demoSessions(), demo = true, labels = true, animated = false, cost = true, duration = true)
                        panel.setSize(JBUI.scale(1200), panel.overlayHeight)
                        val bodyY = JBUI.scale(80)
                        panel.mouse(MouseEvent.MOUSE_MOVED, Point(hitXs(panel, bodyY).first() + JBUI.scale(32), bodyY))
                        assertTrue(paintedXs(panel, JBUI.scale(126)).isNotEmpty())
                        assertTrue(paintedXs(panel, JBUI.scale(145)).isNotEmpty())
                        val image = BufferedImage(panel.width, panel.height, BufferedImage.TYPE_INT_ARGB)
                        val graphics = image.createGraphics()
                        try {
                            graphics.color = Color(30, 31, 34)
                            graphics.fillRect(0, 0, image.width, image.height)
                            panel.paint(graphics)
                        } finally {
                            graphics.dispose()
                        }
                        val output = File("build/reports/air-pets/metrics-${(scale * 100).toInt()}.png")
                        output.parentFile.mkdirs()
                        ImageIO.write(image, "png", output)
                    } finally {
                        panel.dispose()
                    }
                }
            }
        } finally {
            SwingUtilities.invokeAndWait { JBUIScale.setUserScaleFactorForTest(originalScale) }
        }
    }

    @Test
    fun `cost readouts are opt in visible without hover and move with their pets`() {
        val sprites = PetSprites.load()
        val originalScale = JBUIScale.scale(1.0f)
        try {
            for (scale in listOf(1.0f, 2.0f)) {
                SwingUtilities.invokeAndWait {
                    JBUIScale.setUserScaleFactorForTest(scale)
                    val opened = mutableListOf<String>()
                    val done = mutableListOf<String>()
                    val panel = PetOverlay(sprites, opened::add, done::add) { _, _, _ -> }
                    try {
                        panel.setSize(JBUI.scale(600), JBUI.scale(500))
                        val session = PetSession("cost", "Task", PetState.DONE, cost = SessionCost(BigDecimal("1.23")))
                        panel.update(listOf(session), demo = false, labels = false, animated = false, cost = true)
                        val bodyY = panel.height - panel.overlayHeight + JBUI.scale(52)
                        val readoutY = bodyY + JBUI.scale(74)
                        val readoutXs = hitXs(panel, readoutY)
                        assertEquals(panel.getFontMetrics(panel.font).stringWidth("$1.23") + JBUI.scale(10), readoutXs.size)
                        assertTrue("Enabled costs should paint without hovering", paintedXs(panel, readoutY).isNotEmpty())
                        val readout = Point(readoutXs.average().toInt(), readoutY)
                        assertTrue(panel.getToolTipText(MouseEvent(panel, 0, 0, 0, readout.x, readout.y, 0, false)).contains("$1.23"))
                        panel.mouse(MouseEvent.MOUSE_RELEASED, readout)
                        assertEquals(listOf("cost"), opened)
                        assertTrue("Only the heart badge may mark done", done.isEmpty())

                        val delta = Point(JBUI.scale(80), -JBUI.scale(160))
                        val destination = Point(readout.x + delta.x, readout.y + delta.y)
                        panel.mouse(MouseEvent.MOUSE_PRESSED, readout)
                        panel.mouse(MouseEvent.MOUSE_DRAGGED, destination)
                        panel.mouse(MouseEvent.MOUSE_RELEASED, destination)
                        assertEquals("Dragging the cost must not open or finish", listOf("cost"), opened)
                        assertTrue(done.isEmpty())
                        assertTrue(panel.contains(destination.x, destination.y))
                        assertFalse("The old location must pass through", panel.contains(readout.x, readout.y))

                        panel.update(listOf(session.copy(cost = SessionCost(BigDecimal("2.34")))), demo = false, labels = true, animated = false, cost = true)
                        assertTrue("Live costs must not reset placement", panel.contains(destination.x, destination.y))
                        panel.mouse(MouseEvent.MOUSE_MOVED, destination)
                        val labelY = destination.y + JBUI.scale(19)
                        assertTrue("The hovered name belongs below the readout", hitXs(panel, labelY).isNotEmpty())
                        panel.mouse(MouseEvent.MOUSE_EXITED, destination)
                        assertTrue("The name remains hover-only", hitXs(panel, labelY).isEmpty())
                        assertTrue(panel.contains(destination.x, destination.y))

                        panel.update(listOf(session), demo = false, labels = false, animated = false, cost = false)
                        assertFalse("Disabling cost restores click-through", panel.contains(destination.x, destination.y))
                        assertTrue(paintedXs(panel, destination.y).isEmpty())
                    } finally {
                        panel.dispose()
                    }
                }
            }
        } finally {
            SwingUtilities.invokeAndWait { JBUIScale.setUserScaleFactorForTest(originalScale) }
        }
    }

    @Test
    fun `unavailable costs do not create empty readouts`() {
        val sprites = PetSprites.load()
        SwingUtilities.invokeAndWait {
            val panel = PetOverlay(sprites, {}) { _, _, _ -> }
            try {
                panel.setSize(600, 500)
                panel.update(listOf(PetSession("unknown", "Task", PetState.RESTING)), demo = false, labels = false, animated = false, cost = true)
                val readoutY = panel.height - panel.overlayHeight + JBUI.scale(126)
                assertTrue(hitXs(panel, readoutY).isEmpty())
                assertTrue(paintedXs(panel, readoutY).isEmpty())
            } finally {
                panel.dispose()
            }
        }
    }

    @Test
    fun `time readouts refresh with animation disabled and are independent of costs and names`() {
        val sprites = PetSprites.load()
        SwingUtilities.invokeAndWait {
            val panel = PetOverlay(sprites, {}) { _, _, _ -> }
            try {
                panel.setSize(600, 500)
                var session = PetSession("time", "Task", PetState.WORKING, cost = SessionCost(BigDecimal("1.23")), observedWorkingMs = 83_000)
                panel.update(listOf(session), demo = false, labels = false, animated = false, duration = true)
                val y = panel.height - panel.overlayHeight + JBUI.scale(126)
                assertEquals(panel.getFontMetrics(panel.font).stringWidth("1m 23s") + JBUI.scale(10), hitXs(panel, y).size)
                assertTrue(paintedXs(panel, y).isNotEmpty())
                session = session.copy(observedWorkingMs = 125_000)
                panel.update(listOf(session), demo = false, labels = false, animated = false, duration = true)
                val x = hitXs(panel, y).average().toInt()
                val tooltip = panel.getToolTipText(MouseEvent(panel, 0, 0, 0, x, y, 0, false))
                assertTrue(tooltip.contains("Observed working time: 2m 5s"))
                assertFalse(tooltip.contains("$1.23"))
                panel.update(listOf(session), demo = false, labels = false, animated = false, cost = true, duration = true)
                assertEquals(panel.getFontMetrics(panel.font).stringWidth("$1.23 · 2m 5s") + JBUI.scale(10), hitXs(panel, y).size)
                panel.update(listOf(session), demo = false, labels = false, animated = false, cost = true)
                assertEquals(panel.getFontMetrics(panel.font).stringWidth("$1.23") + JBUI.scale(10), hitXs(panel, y).size)
            } finally {
                panel.dispose()
            }
        }
    }

    @Test
    fun `cost time and hovered name remain bounded when dragged to edges at both zoom levels`() {
        val sprites = PetSprites.load()
        val originalScale = JBUIScale.scale(1.0f)
        try {
            for (scale in listOf(1.0f, 2.0f)) {
                SwingUtilities.invokeAndWait {
                    JBUIScale.setUserScaleFactorForTest(scale)
                    val panel = PetOverlay(sprites, {}) { _, _, _ -> }
                    try {
                        panel.setSize(JBUI.scale(600), JBUI.scale(500))
                        val session = PetSession("metrics", "Task", PetState.WORKING, cost = SessionCost(BigDecimal("1.23")), observedWorkingMs = 83_000)
                        panel.update(listOf(session), demo = false, labels = true, animated = false, cost = true, duration = true)
                        var bodyY = panel.height - panel.overlayHeight + JBUI.scale(52)
                        var body = Point(hitXs(panel, bodyY + JBUI.scale(32)).average().toInt(), bodyY + JBUI.scale(32))
                        for (edge in listOf(-4000, 8000)) {
                            panel.mouse(MouseEvent.MOUSE_PRESSED, body)
                            panel.mouse(MouseEvent.MOUSE_DRAGGED, Point(edge, edge))
                            panel.mouse(MouseEvent.MOUSE_RELEASED, Point(edge, edge))
                            bodyY = if (edge < 0) JBUI.scale(20) else panel.height - JBUI.scale(102)
                            val bodyXs = hitXs(panel, bodyY + JBUI.scale(32))
                            assertEquals(JBUI.scale(64), bodyXs.size)
                            body = Point(bodyXs.average().toInt(), bodyY + JBUI.scale(32))
                            panel.mouse(MouseEvent.MOUSE_MOVED, body)
                            val readout = hitXs(panel, bodyY + JBUI.scale(74))
                            assertEquals(panel.getFontMetrics(panel.font).stringWidth("$1.23 · 1m 23s") + JBUI.scale(10), readout.size)
                            assertTrue(paintedXs(panel, bodyY + JBUI.scale(74)).isNotEmpty())
                            val name = hitXs(panel, bodyY + JBUI.scale(93))
                            assertEquals(panel.getFontMetrics(panel.font).stringWidth("Task") + JBUI.scale(10), name.size)
                            assertTrue(readout.first() >= 0 && readout.last() < panel.width)
                            assertTrue(name.first() >= 0 && name.last() < panel.width)
                        }
                        panel.setSize(JBUI.scale(400), JBUI.scale(300))
                        assertEquals(panel.getFontMetrics(panel.font).stringWidth("$1.23 · 1m 23s") + JBUI.scale(10), hitXs(panel, panel.height - JBUI.scale(28)).size)
                    } finally {
                        panel.dispose()
                    }
                }
            }
        } finally {
            SwingUtilities.invokeAndWait { JBUIScale.setUserScaleFactorForTest(originalScale) }
        }
    }

    @Test
    fun `session names appear only beneath hovered pets`() {
        val sprites = PetSprites.load()
        val originalScale = JBUIScale.scale(1.0f)
        try {
            for (scale in listOf(1.0f, 2.0f)) {
                SwingUtilities.invokeAndWait {
                    JBUIScale.setUserScaleFactorForTest(scale)
                    val panel = PetOverlay(sprites, {}) { _, _, _ -> }
                    try {
                        panel.setSize(JBUI.scale(1200), panel.overlayHeight)
                        for (count in listOf(1, 3)) {
                            panel.mouse(MouseEvent.MOUSE_EXITED, Point())
                            val sessions = (1..count).map { PetSession("pet-$it", "Task", PetState.WORKING) }
                            panel.update(sessions, demo = false, labels = true, animated = false)
                            val bodyY = JBUI.scale(80)
                            val labelY = JBUI.scale(125)
                            val bodyXs = hitXs(panel, bodyY)
                            val body = Point(bodyXs.first() + JBUI.scale(32), bodyY)
                            assertTrue("Names must not intercept clicks before hovering", hitXs(panel, labelY).isEmpty())
                            assertTrue("Names must not be painted before hovering", paintedXs(panel, labelY).isEmpty())

                            panel.mouse(MouseEvent.MOUSE_MOVED, body)
                            val labelXs = hitXs(panel, labelY)
                            assertEquals("Only the hovered name must be interactive at zoom $scale", panel.getFontMetrics(panel.font).stringWidth("Task") + JBUI.scale(10), labelXs.size)
                            assertEquals("The name must be centered beneath its pet, not its lane", body.x.toDouble(), (labelXs.first() + labelXs.last() + 1) / 2.0, 0.5)
                            assertTrue("The hovered name must be painted", paintedXs(panel, labelY).isNotEmpty())
                            assertEquals("Revealing a name must not reposition any pet", bodyXs, hitXs(panel, bodyY))

                            val label = Point(labelXs.average().toInt(), labelY)
                            panel.mouse(MouseEvent.MOUSE_MOVED, Point(label.x, JBUI.scale(116)))
                            assertEquals("Crossing the gap below the pet must keep the name visible", labelXs, hitXs(panel, labelY))
                            panel.mouse(MouseEvent.MOUSE_MOVED, label)
                            assertEquals("Moving onto the revealed name must keep it visible", labelXs, hitXs(panel, labelY))
                            panel.mouse(MouseEvent.MOUSE_MOVED, Point(0, 0))
                            assertTrue("Leaving the pet and label must restore click-through", hitXs(panel, labelY).isEmpty())
                            assertTrue("Leaving must also stop painting the name", paintedXs(panel, labelY).isEmpty())
                            panel.mouse(MouseEvent.MOUSE_MOVED, label)
                            assertTrue("A hidden name must not reveal itself or intercept editor input", hitXs(panel, labelY).isEmpty())

                            panel.mouse(MouseEvent.MOUSE_MOVED, body)
                            panel.update(sessions, demo = false, labels = false, animated = false)
                            assertTrue("Disabling names must suppress even the hovered label", hitXs(panel, labelY).isEmpty())
                            assertTrue(paintedXs(panel, labelY).isEmpty())
                            panel.update(sessions, demo = false, labels = true, animated = false)
                            panel.mouse(MouseEvent.MOUSE_EXITED, body)
                            assertTrue("Leaving the overlay must hide the name", hitXs(panel, labelY).isEmpty())
                            panel.mouse(MouseEvent.MOUSE_MOVED, Point(body.x + JBUI.scale(33), JBUI.scale(42)))
                            assertEquals("Hovering the status badge must also reveal its pet's name", labelXs, hitXs(panel, labelY))
                        }
                    } finally {
                        panel.dispose()
                    }
                }
            }
        } finally {
            SwingUtilities.invokeAndWait { JBUIScale.setUserScaleFactorForTest(originalScale) }
        }
    }

    @Test
    fun `long hovered names stay compact beneath edge placed pets without moving them`() {
        val sprites = PetSprites.load()
        val originalScale = JBUIScale.scale(1.0f)
        try {
            for (scale in listOf(1.0f, 2.0f)) {
                SwingUtilities.invokeAndWait {
                    JBUIScale.setUserScaleFactorForTest(scale)
                    val panel = PetOverlay(sprites, {}) { _, _, _ -> }
                    try {
                        panel.setSize(JBUI.scale(600), JBUI.scale(500))
                        val title = "A very long session name ".repeat(12)
                        panel.update(listOf(PetSession("long-name", title, PetState.WORKING)), demo = false, labels = true, animated = false)
                        val initialY = panel.height - panel.overlayHeight + JBUI.scale(80)
                        var body = Point(hitXs(panel, initialY).average().toInt(), initialY)
                        for (edge in listOf(-2000, 4000)) {
                            panel.mouse(MouseEvent.MOUSE_PRESSED, body)
                            panel.mouse(MouseEvent.MOUSE_DRAGGED, Point(edge, edge))
                            panel.mouse(MouseEvent.MOUSE_RELEASED, Point(edge, edge))
                            val bodyY = if (edge < 0) JBUI.scale(20) else panel.height - JBUI.scale(83)
                            val bodyXs = hitXs(panel, bodyY + JBUI.scale(32))
                            body = Point(bodyXs.average().toInt(), bodyY + JBUI.scale(32))
                            panel.mouse(MouseEvent.MOUSE_MOVED, body)
                            val labelY = bodyY + JBUI.scale(74)
                            val labelXs = hitXs(panel, labelY)
                            assertTrue("A long name must remain compact at zoom $scale", labelXs.isNotEmpty() && labelXs.size <= JBUI.scale(240))
                            if (edge < 0) assertEquals("The left-edge label must not be clipped", 0, labelXs.first())
                            else assertEquals("The right-edge label must not be clipped", panel.width - 1, labelXs.last())
                            assertTrue("The label must start directly below the pet", panel.contains(labelXs.average().toInt(), bodyY + JBUI.scale(65)))
                            assertTrue("The full label height must remain visible", panel.contains(labelXs.average().toInt(), bodyY + JBUI.scale(83) - 1))
                            assertEquals("Showing a wide name must not move the pet", bodyXs, hitXs(panel, body.y))
                            assertTrue("The tooltip must retain the full name", panel.getToolTipText(MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, body.x, body.y, 0, false)).contains(title))
                            panel.mouse(MouseEvent.MOUSE_EXITED, body)
                            assertTrue(hitXs(panel, labelY).isEmpty())
                            assertTrue(paintedXs(panel, labelY).isEmpty())
                            assertEquals("Hiding the name must not move the pet", bodyXs, hitXs(panel, body.y))
                        }
                    } finally {
                        panel.dispose()
                    }
                }
            }
        } finally {
            SwingUtilities.invokeAndWait { JBUIScale.setUserScaleFactorForTest(originalScale) }
        }
    }

    @Test
    fun `completed heart becomes a mark as done control only while hovered`() {
        val sprites = PetSprites.load()
        SwingUtilities.invokeAndWait {
            val panel = PetOverlay(sprites, {}) { _, _, _ -> }
            try {
                panel.setSize(JBUI.scale(400), panel.overlayHeight)
                panel.update(listOf(PetSession("completed", "Completed task", PetState.DONE)), demo = false, labels = false, animated = false)
                val badgeY = JBUI.scale(42)
                val badgeX = (0 until panel.width).first { panel.contains(it, badgeY) } + JBUI.scale(14)
                fun pointer(type: Int, x: Int = badgeX, y: Int = badgeY) =
                    MouseEvent(panel, type, 0, 0, x, y, 0, false)
                fun rendered(): List<Int> {
                    val image = BufferedImage(panel.width, panel.height, BufferedImage.TYPE_INT_ARGB)
                    val graphics = image.createGraphics()
                    try {
                        panel.paint(graphics)
                    } finally {
                        graphics.dispose()
                    }
                    return image.getRGB(0, 0, image.width, image.height, null, 0, image.width).toList()
                }

                val heart = rendered()
                panel.dispatchEvent(pointer(MouseEvent.MOUSE_MOVED))
                assertTrue(panel.getToolTipText(pointer(MouseEvent.MOUSE_MOVED)).contains("Mark as done"))
                assertNotEquals("Hovering the heart must show a checkmark", heart, rendered())
                val bodyY = JBUI.scale(80)
                val bodyX = (0 until panel.width).first { panel.contains(it, bodyY) } + JBUI.scale(10)
                panel.dispatchEvent(pointer(MouseEvent.MOUSE_MOVED, x = bodyX, y = bodyY))
                assertEquals("Hovering the pet must not replace the heart", heart, rendered())
                assertTrue(panel.getToolTipText(pointer(MouseEvent.MOUSE_MOVED, x = bodyX, y = bodyY)).contains("Click to open in AIR"))
                panel.dispatchEvent(pointer(MouseEvent.MOUSE_MOVED))
                panel.dispatchEvent(pointer(MouseEvent.MOUSE_EXITED))
                assertEquals("Leaving the overlay must restore the heart", heart, rendered())
            } finally {
                panel.dispose()
            }
        }
    }

    @Test
    fun `only the completed badge marks done while pet label and other states still open`() {
        val sprites = PetSprites.load()
        val originalScale = JBUIScale.scale(1.0f)
        try {
            for (scale in listOf(1.0f, 2.0f)) {
                SwingUtilities.invokeAndWait {
                    JBUIScale.setUserScaleFactorForTest(scale)
                    val opened = mutableListOf<String>()
                    val finished = mutableListOf<String>()
                    var menus = 0
                    val panel = PetOverlay(sprites, opened::add, finished::add) { _, _, _ -> menus++ }
                    try {
                        panel.setSize(JBUI.scale(400), panel.overlayHeight)
                        for (state in PetState.entries) {
                            val session = PetSession("session", "Task", state)
                            panel.update(listOf(session), demo = false, labels = true, animated = false)
                            val badgeY = JBUI.scale(42)
                            val badgeX = (0 until panel.width).first { panel.contains(it, badgeY) } + JBUI.scale(14)
                            fun click(x: Int, y: Int, button: Int = MouseEvent.BUTTON1) {
                                panel.dispatchEvent(MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, x, y, 1, button == MouseEvent.BUTTON3, button))
                            }

                            opened.clear()
                            finished.clear()
                            click(badgeX, badgeY)
                            assertEquals("Only a completed badge may finish at zoom $scale", if (state == PetState.DONE) listOf(session.id) else emptyList<String>(), finished)
                            assertEquals("Finishing must not also open the session", if (state == PetState.DONE) emptyList<String>() else listOf(session.id), opened)
                            opened.clear()
                            finished.clear()
                            val bodyY = JBUI.scale(80)
                            val bodyX = (0 until panel.width).first { panel.contains(it, bodyY) } + JBUI.scale(10)
                            click(bodyX, bodyY)
                            val labelY = JBUI.scale(125)
                            val labelX = (0 until panel.width).first { panel.contains(it, labelY) }
                            click(labelX, labelY)
                            assertEquals(listOf(session.id, session.id), opened)
                            click(badgeX, badgeY, MouseEvent.BUTTON3)
                            assertTrue("Opening the context menu must never finish a session", finished.isEmpty())
                            assertTrue(menus > 0)
                            click(0, 0)
                            assertTrue("Empty space must not finish a session", finished.isEmpty())
                        }
                        panel.update(emptyList(), demo = false, labels = false, animated = false)
                        panel.dispatchEvent(MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, JBUI.scale(100), JBUI.scale(42), 1, false, MouseEvent.BUTTON1))
                        assertTrue("A removed session must not remain actionable", finished.isEmpty())
                    } finally {
                        panel.dispose()
                    }
                }
            }
        } finally {
            SwingUtilities.invokeAndWait { JBUIScale.setUserScaleFactorForTest(originalScale) }
        }
    }

    @Test
    fun `dragging from the pet label or badge moves the whole group without activating it`() {
        val sprites = PetSprites.load()
        val originalScale = JBUIScale.scale(1.0f)
        try {
            for (scale in listOf(1.0f, 2.0f)) {
                SwingUtilities.invokeAndWait {
                    JBUIScale.setUserScaleFactorForTest(scale)
                    for (sourceY in listOf(80, 125, 42)) {
                        val opened = mutableListOf<String>()
                        val finished = mutableListOf<String>()
                        val panel = PetOverlay(sprites, opened::add, finished::add) { _, _, _ -> }
                        try {
                            panel.setSize(JBUI.scale(600), JBUI.scale(500))
                            panel.update(listOf(PetSession("completed", "Task", PetState.DONE)), demo = false, labels = true, animated = false)
                            val bottom = panel.height - panel.overlayHeight
                            val bodyY = bottom + JBUI.scale(80)
                            panel.mouse(MouseEvent.MOUSE_MOVED, Point(hitXs(panel, bodyY).average().toInt(), bodyY))
                            val rows = listOf(42, 80, 125).associate { y -> bottom + JBUI.scale(y) to hitXs(panel, bottom + JBUI.scale(y)) }
                            assertTrue("Default pets must stay near the bottom", rows.values.all { it.isNotEmpty() })
                            val pressY = bottom + JBUI.scale(sourceY)
                            val press = Point(rows.getValue(pressY).average().toInt(), pressY)
                            val dx = JBUI.scale(if (rows.values.flatten().max() + JBUI.scale(40) < panel.width) 30 else -30)
                            val dy = -JBUI.scale(180)
                            val drop = Point(press.x + dx, press.y + dy)
                            panel.mouse(MouseEvent.MOUSE_PRESSED, press)
                            panel.mouse(MouseEvent.MOUSE_DRAGGED, drop)
                            assertEquals(Cursor.MOVE_CURSOR, panel.cursor.type)
                            panel.mouse(MouseEvent.MOUSE_RELEASED, drop)
                            assertEquals(Cursor.HAND_CURSOR, panel.cursor.type)
                            for ((y, xs) in rows) {
                                assertEquals("The badge, body and label must move together at zoom $scale", xs.map { it + dx }, hitXs(panel, y + dy))
                                assertTrue("The old location must become click-through", hitXs(panel, y).isEmpty())
                            }
                            assertTrue("A drag must not open AIR", opened.isEmpty())
                            assertTrue("Dragging a completed badge must not mark it done", finished.isEmpty())
                            val body = Point(rows.getValue(bottom + JBUI.scale(80)).average().toInt() + dx, bottom + JBUI.scale(80) + dy)
                            val badge = Point(rows.getValue(bottom + JBUI.scale(42)).average().toInt() + dx, bottom + JBUI.scale(42) + dy)
                            val label = Point(rows.getValue(bottom + JBUI.scale(125)).average().toInt() + dx, bottom + JBUI.scale(125) + dy)
                            panel.mouse(MouseEvent.MOUSE_PRESSED, body)
                            panel.mouse(MouseEvent.MOUSE_RELEASED, body)
                            panel.mouse(MouseEvent.MOUSE_PRESSED, label)
                            panel.mouse(MouseEvent.MOUSE_RELEASED, label)
                            panel.mouse(MouseEvent.MOUSE_PRESSED, badge)
                            panel.mouse(MouseEvent.MOUSE_RELEASED, badge)
                            assertEquals(listOf("completed", "completed"), opened)
                            assertEquals(listOf("completed"), finished)
                            assertTrue(panel.getToolTipText(MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, body.x, body.y, 0, false)).contains("Drag"))
                        } finally {
                            panel.dispose()
                        }
                    }
                }
            }
        } finally {
            SwingUtilities.invokeAndWait { JBUIScale.setUserScaleFactorForTest(originalScale) }
        }
    }

    @Test
    fun `manual positions survive refresh state changes and temporarily missing sessions`() {
        val sprites = PetSprites.load()
        SwingUtilities.invokeAndWait {
            val panel = PetOverlay(sprites, {}) { _, _, _ -> }
            val session = PetSession("placed", "Task", PetState.WORKING)
            try {
                panel.setSize(JBUI.scale(900), JBUI.scale(700))
                panel.update(listOf(session), demo = false, labels = true, animated = false)
                val bottom = panel.height - panel.overlayHeight
                val bodyY = bottom + JBUI.scale(80)
                val bodyXs = hitXs(panel, bodyY)
                val press = Point(bodyXs.average().toInt(), bodyY)
                val drop = Point(press.x, press.y - JBUI.scale(250))
                panel.mouse(MouseEvent.MOUSE_PRESSED, press)
                panel.mouse(MouseEvent.MOUSE_DRAGGED, drop)
                panel.mouse(MouseEvent.MOUSE_RELEASED, drop)
                val labelY = bottom + JBUI.scale(125) - JBUI.scale(250)
                val labelXs = hitXs(panel, labelY)
                for (state in PetState.entries) {
                    panel.update(listOf(PetSession("other", "Other", PetState.WORKING), session.copy(state = state, title = "Updated task")), demo = false, labels = true, animated = true)
                    assertEquals("Refreshing or animating must not move a placed pet", bodyXs, hitXs(panel, drop.y))
                    val refreshedLabel = hitXs(panel, labelY)
                    assertEquals("A changed title must keep the label centered at the dropped location", (labelXs.first() + labelXs.last()) / 2.0, (refreshedLabel.first() + refreshedLabel.last()) / 2.0, 0.5)
                }
                panel.update(listOf(session), demo = false, labels = false, animated = false)
                assertTrue("Hidden labels must remain click-through", hitXs(panel, labelY).isEmpty())
                assertEquals(bodyXs, hitXs(panel, drop.y))
                panel.update(emptyList(), demo = false, labels = true, animated = false)
                assertFalse(panel.contains(drop))
                panel.update(listOf(session), demo = false, labels = true, animated = false)
                assertEquals("A transiently missing session must retain its manual position", bodyXs, hitXs(panel, drop.y))
                assertEquals(labelXs, hitXs(panel, labelY))
            } finally {
                panel.dispose()
            }
        }
    }

    @Test
    fun `subthreshold movement and non-left gestures do not reposition pets`() {
        val sprites = PetSprites.load()
        val originalScale = JBUIScale.scale(1.0f)
        try {
            for (scale in listOf(1.0f, 2.0f)) {
                SwingUtilities.invokeAndWait {
                    JBUIScale.setUserScaleFactorForTest(scale)
                    val opened = mutableListOf<String>()
                    val finished = mutableListOf<String>()
                    var menus = 0
                    val panel = PetOverlay(sprites, opened::add, finished::add) { _, _, _ -> menus++ }
                    try {
                        panel.setSize(JBUI.scale(600), panel.overlayHeight)
                        panel.update(listOf(PetSession("completed", "Task", PetState.DONE)), demo = false, labels = true, animated = false)
                        val bodyY = JBUI.scale(80)
                        val bodyXs = hitXs(panel, bodyY)
                        val body = Point(bodyXs.average().toInt(), bodyY)
                        val badge = Point(hitXs(panel, JBUI.scale(42)).average().toInt(), JBUI.scale(42))
                        for (point in listOf(body, badge)) {
                            val release = Point(point.x + JBUI.scale(4), point.y)
                            panel.mouse(MouseEvent.MOUSE_PRESSED, point)
                            panel.mouse(MouseEvent.MOUSE_DRAGGED, release)
                            assertEquals(Cursor.HAND_CURSOR, panel.cursor.type)
                            panel.mouse(MouseEvent.MOUSE_RELEASED, release)
                        }
                        assertEquals(listOf("completed"), opened)
                        assertEquals(listOf("completed"), finished)
                        panel.mouse(MouseEvent.MOUSE_PRESSED, body, MouseEvent.BUTTON3, popup = true)
                        panel.mouse(MouseEvent.MOUSE_DRAGGED, Point(body.x, 0), MouseEvent.BUTTON3)
                        panel.mouse(MouseEvent.MOUSE_RELEASED, body, MouseEvent.BUTTON3, popup = true)
                        assertEquals("Popup triggers must retain their existing behavior", 2, menus)
                        panel.mouse(MouseEvent.MOUSE_PRESSED, Point(0, 0))
                        panel.mouse(MouseEvent.MOUSE_DRAGGED, Point(JBUI.scale(100), 0))
                        panel.mouse(MouseEvent.MOUSE_RELEASED, Point(JBUI.scale(100), 0))
                        panel.mouse(MouseEvent.MOUSE_DRAGGED, Point(body.x, 0))
                        panel.mouse(MouseEvent.MOUSE_RELEASED, Point(body.x, 0))
                        panel.mouse(MouseEvent.MOUSE_PRESSED, body)
                        panel.mouse(MouseEvent.MOUSE_DRAGGED, Point(body.x, body.y - JBUI.scale(30)))
                        panel.mouse(MouseEvent.MOUSE_DRAGGED, body)
                        panel.mouse(MouseEvent.MOUSE_RELEASED, body)
                        assertEquals("Only a left press on a pet may start a drag", bodyXs, hitXs(panel, bodyY))
                        assertEquals("Dragging back to the press point must still suppress opening", listOf("completed"), opened)
                        assertEquals("Dragging back to the press point must still suppress completion", listOf("completed"), finished)
                    } finally {
                        panel.dispose()
                    }
                }
            }
        } finally {
            SwingUtilities.invokeAndWait { JBUIScale.setUserScaleFactorForTest(originalScale) }
        }
    }

    @Test
    fun `full pane overlay passes empty editor and status bar clicks to IDE content`() {
        val sprites = PetSprites.load()
        SwingUtilities.invokeAndWait {
            val layer = JLayeredPane().apply { setSize(JBUI.scale(900), JBUI.scale(700)) }
            val content = JPanel().apply { setBounds(0, 0, layer.width, layer.height) }
            val panel = PetOverlay(sprites, {}) { _, _, _ -> }
            try {
                layer.add(content)
                layer.addPetOverlay(panel)
                panel.update(listOf(PetSession("placed", "Task", PetState.WORKING)), demo = false, labels = true, animated = false)
                val editor = Point(JBUI.scale(450), JBUI.scale(100))
                val status = Point(JBUI.scale(450), layer.height - JBUI.scale(10))
                assertSame(content, SwingUtilities.getDeepestComponentAt(layer, editor.x, editor.y))
                assertSame(content, SwingUtilities.getDeepestComponentAt(layer, status.x, status.y))
                val bodyY = panel.height - panel.overlayHeight + JBUI.scale(80)
                val body = Point(hitXs(panel, bodyY).average().toInt(), bodyY)
                assertSame(panel, SwingUtilities.getDeepestComponentAt(layer, body.x, body.y))
                panel.mouse(MouseEvent.MOUSE_PRESSED, body)
                panel.mouse(MouseEvent.MOUSE_DRAGGED, editor)
                panel.mouse(MouseEvent.MOUSE_RELEASED, editor)
                assertSame("Only the dropped pet should intercept editor clicks", panel, SwingUtilities.getDeepestComponentAt(layer, editor.x, editor.y))
                assertSame("The vacated pet location must pass through", content, SwingUtilities.getDeepestComponentAt(layer, body.x, body.y))
                assertSame("The status bar must never be covered by the overlay", content, SwingUtilities.getDeepestComponentAt(layer, status.x, status.y))
            } finally {
                panel.dispose()
                layer.remove(panel)
            }
        }
    }

    @Test
    fun `placed groups stay inside the full overlay on edge drops zoom and resize`() {
        val sprites = PetSprites.load()
        val originalScale = JBUIScale.scale(1.0f)
        val layer = JLayeredPane()
        var panel: PetOverlay? = null
        try {
            SwingUtilities.invokeAndWait {
                JBUIScale.setUserScaleFactorForTest(1.0f)
                layer.setSize(1200, 900)
                panel = PetOverlay(sprites, {}) { _, _, _ -> }.apply {
                    layer.addPetOverlay(this)
                    update(listOf(PetSession("placed", "Task", PetState.DONE)), demo = false, labels = true, animated = false)
                    val y = height - overlayHeight + JBUI.scale(80)
                    val press = Point(hitXs(this, y).average().toInt(), y)
                    mouse(MouseEvent.MOUSE_PRESSED, press)
                    mouse(MouseEvent.MOUSE_DRAGGED, Point(-2000, -2000))
                    mouse(MouseEvent.MOUSE_RELEASED, Point(-2000, -2000))
                    assertPlacedGroup(this, JBUI.scale(20))
                    val body = Point(hitXs(this, JBUI.scale(52)).average().toInt(), JBUI.scale(52))
                    mouse(MouseEvent.MOUSE_PRESSED, body)
                    mouse(MouseEvent.MOUSE_DRAGGED, Point(4000, 4000))
                    mouse(MouseEvent.MOUSE_RELEASED, Point(4000, 4000))
                    assertPlacedGroup(this, height - JBUI.scale(83))
                }
            }
            for (scale in listOf(2.0f, 1.25f, 1.0f)) {
                SwingUtilities.invokeAndWait { JBUIScale.setUserScaleFactorForTest(scale) }
                SwingUtilities.invokeAndWait {
                    val attachedPanel = requireNotNull(panel)
                    assertEquals(0, attachedPanel.y)
                    assertEquals(layer.height - JBUI.scale(28), attachedPanel.height)
                    assertPlacedGroup(attachedPanel, attachedPanel.height - JBUI.scale(83))
                    layer.setSize(700, 600)
                    attachedPanel.positionInParent()
                    assertEquals(layer.width, attachedPanel.width)
                    assertEquals(layer.height - JBUI.scale(28), attachedPanel.height)
                    assertPlacedGroup(attachedPanel, attachedPanel.height - JBUI.scale(83))
                }
            }
        } finally {
            SwingUtilities.invokeAndWait {
                panel?.let {
                    it.dispose()
                    layer.remove(it)
                }
                JBUIScale.setUserScaleFactorForTest(originalScale)
            }
        }
    }

    @Test
    fun `zooming keeps pets and labels inside the overlay without resizing the window`() {
        val sprites = PetSprites.load()
        val originalScale = JBUIScale.scale(1.0f)
        var panel: PetOverlay? = null
        val layer = JLayeredPane()
        try {
            SwingUtilities.invokeAndWait {
                JBUIScale.setUserScaleFactorForTest(1.0f)
                layer.setSize(1200, 900)
                panel = PetOverlay(sprites, {}) { _, _, _ -> }.apply {
                    layer.addPetOverlay(this)
                    update(demoSessions(), demo = true, labels = true, animated = false)
                }
            }
            for (scale in listOf(1.5f, 2.0f, 1.25f, 1.75f, 1.0f)) {
                SwingUtilities.invokeAndWait { JBUIScale.setUserScaleFactorForTest(scale) }
                SwingUtilities.invokeAndWait {
                    val attachedPanel = requireNotNull(panel)
                    assertEquals("Zoom $scale must resize the full overlay above the status bar", layer.height - JBUI.scale(28), attachedPanel.height)
                    assertEquals("The overlay must cover the usable IDE area", 0, attachedPanel.y)
                    assertEquals("The overlay must stay above the status bar", layer.height - JBUI.scale(28), attachedPanel.y + attachedPanel.height)
                    assertEquals(1200, attachedPanel.width)
                    val bodyY = attachedPanel.height - attachedPanel.overlayHeight + JBUI.scale(80)
                    attachedPanel.mouse(MouseEvent.MOUSE_MOVED, Point(hitXs(attachedPanel, bodyY).first() + JBUI.scale(32), bodyY))
                    val image = BufferedImage(attachedPanel.width, attachedPanel.height, BufferedImage.TYPE_INT_ARGB)
                    val graphics = image.createGraphics()
                    try {
                        attachedPanel.paint(graphics)
                    } finally {
                        graphics.dispose()
                    }
                    val labelY = attachedPanel.height - attachedPanel.overlayHeight + JBUI.scale(125)
                    assertTrue("Session labels must render without being clipped at zoom $scale",
                        (0 until image.width).any { image.getRGB(it, labelY).ushr(24) > 0 })
                    assertTrue("The full label height must remain inside the overlay at zoom $scale", labelY + JBUI.scale(10) <= image.height)
                    assertFalse("Empty editor space must remain click-through at zoom $scale", attachedPanel.contains(600, 100))
                    if (scale == 1.5f || scale == 2.0f) {
                        val output = File("build/reports/air-pets/zoom-${(scale * 100).toInt()}.png")
                        output.parentFile.mkdirs()
                        ImageIO.write(image, "png", output)
                    }
                }
            }
        } finally {
            SwingUtilities.invokeAndWait {
                panel?.let {
                    it.dispose()
                    layer.remove(it)
                }
                JBUIScale.setUserScaleFactorForTest(originalScale)
            }
        }
    }

    @Test
    fun `pet overlay is above IDE content and below popups`() {
        val sprites = PetSprites.load()
        SwingUtilities.invokeAndWait {
            val panel = PetOverlay(sprites, {}) { _, _, _ -> }
            val layer = JLayeredPane()
            try {
                layer.addPetOverlay(panel)
                assertEquals(JLayeredPane.PALETTE_LAYER.toInt(), layer.getLayer(panel))
                assertTrue(layer.getLayer(panel) > JLayeredPane.DEFAULT_LAYER.toInt())
                assertTrue(layer.getLayer(panel) < JLayeredPane.POPUP_LAYER.toInt())
            } finally {
                panel.dispose()
                layer.remove(panel)
            }
        }
    }

    @Test
    fun `overlay renders all demo states and empty areas pass through`() {
        val sprites = PetSprites.load()
        SwingUtilities.invokeAndWait {
            val opened = mutableListOf<String>()
            var menuOpened = false
            val panel = PetOverlay(sprites, opened::add) { _, _, _ -> menuOpened = true }
            try {
                panel.setSize(1200, panel.overlayHeight)
                panel.update(demoSessions(), demo = true, labels = true, animated = false)
                assertFalse("Empty editor space must not intercept clicks", panel.contains(4, 40))
                assertFalse("Empty space beside labels must pass through", panel.contains(14, 125))
                assertFalse("The former floating controls must pass clicks through", panel.contains(1100, 10))
                val image = BufferedImage(1200, 180, BufferedImage.TYPE_INT_ARGB)
                val graphics = image.createGraphics()
                graphics.color = Color(30, 31, 34)
                graphics.fillRect(0, 0, image.width, image.height)
                panel.paint(graphics)
                graphics.dispose()
                val output = File("build/reports/air-pets/demo.png")
                output.parentFile.mkdirs()
                ImageIO.write(image, "png", output)
                panel.dispatchEvent(MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, 1100, 10, 1, false, MouseEvent.BUTTON1))
                assertFalse(menuOpened)
                val targetX = (0 until 1200).first { panel.contains(it, 80) }
                panel.dispatchEvent(MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, targetX, 80, 1, false, MouseEvent.BUTTON1))
                assertEquals(1, opened.size)
                assertTrue(opened.single().startsWith("demo-"))
                panel.dispatchEvent(MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, targetX, 80, 1, true, MouseEvent.BUTTON3))
                assertTrue("Pets retain their context menu", menuOpened)
                panel.update(emptyList(), demo = false, labels = false, animated = false)
                assertFalse(panel.contains(targetX, 80))
            } finally {
                panel.dispose()
            }
        }
    }

    @Test
    fun `empty overlay does not paint or intercept a floating preferences button`() {
        val sprites = PetSprites.load()
        SwingUtilities.invokeAndWait {
            val panel = PetOverlay(sprites, {}) { _, _, _ -> }
            try {
                panel.setSize(1200, panel.overlayHeight)
                panel.update(emptyList(), demo = false, labels = true, animated = false)
                assertFalse(panel.contains(1100, 10))
                val image = BufferedImage(panel.width, panel.height, BufferedImage.TYPE_INT_ARGB)
                val graphics = image.createGraphics()
                try {
                    panel.paint(graphics)
                } finally {
                    graphics.dispose()
                }
                assertTrue("No floating controls should be drawn", image.getRGB(0, 0, image.width, image.height, null, 0, image.width).all { it == 0 })
            } finally {
                panel.dispose()
            }
        }
    }

    private fun hitXs(panel: PetOverlay, y: Int) = (0 until panel.width).filter { panel.contains(it, y) }

    private fun paintedXs(panel: PetOverlay, y: Int): List<Int> {
        val image = BufferedImage(panel.width, panel.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            panel.paint(graphics)
        } finally {
            graphics.dispose()
        }
        return (0 until image.width).filter { image.getRGB(it, y).ushr(24) > 0 }
    }

    private fun PetOverlay.mouse(type: Int, point: Point, button: Int = MouseEvent.BUTTON1, popup: Boolean = false) {
        val modifiers = if (type == MouseEvent.MOUSE_PRESSED || type == MouseEvent.MOUSE_DRAGGED) {
            if (button == MouseEvent.BUTTON1) InputEvent.BUTTON1_DOWN_MASK else InputEvent.BUTTON3_DOWN_MASK
        } else 0
        dispatchEvent(MouseEvent(this, type, 0, modifiers, point.x, point.y, 1, popup, if (type == MouseEvent.MOUSE_DRAGGED) MouseEvent.NOBUTTON else button))
    }

    private fun assertPlacedGroup(panel: PetOverlay, bodyY: Int) {
        val badge = hitXs(panel, bodyY - JBUI.scale(9))
        val body = hitXs(panel, bodyY + JBUI.scale(32))
        panel.mouse(MouseEvent.MOUSE_MOVED, Point(body.average().toInt(), bodyY + JBUI.scale(32)))
        val label = hitXs(panel, bodyY + JBUI.scale(74))
        assertEquals("The whole badge must fit inside the overlay", JBUI.scale(28), badge.size)
        assertEquals("The whole pet must fit inside the overlay", JBUI.scale(64), body.size)
        assertEquals("The whole label must fit inside the overlay", panel.getFontMetrics(panel.font).stringWidth("Task") + JBUI.scale(10), label.size)
        assertTrue(panel.contains(badge.average().toInt(), bodyY - JBUI.scale(20)))
        assertTrue(panel.contains(body.average().toInt(), bodyY + JBUI.scale(64) - 1))
        assertTrue(panel.contains(label.average().toInt(), bodyY + JBUI.scale(83) - 1))
    }
}