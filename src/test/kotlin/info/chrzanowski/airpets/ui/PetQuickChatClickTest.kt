package info.chrzanowski.airpets.ui

import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import info.chrzanowski.airpets.model.PetSession
import info.chrzanowski.airpets.model.PetState
import org.junit.Assert.*
import org.junit.Test
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

class PetQuickChatClickTest {
    @Test
    fun `live clicks open quick chat with the pet anchor but completed badges still finish`() = atBothScales {
        val opened = mutableListOf<String>()
        val finished = mutableListOf<String>()
        val chats = mutableListOf<Pair<String, Rectangle>>()
        val panel = PetOverlay(PetSprites.load(), opened::add, finished::add, { id, anchor -> chats += id to anchor }) { _, _, _ -> }
        try {
            panel.setSize(JBUI.scale(600), panel.overlayHeight)
            for (state in PetState.entries) {
                panel.update(listOf(PetSession("session", "Task", state)), false, true, false)
                val body = panel.pointAt(80)
                panel.mouse(MouseEvent.MOUSE_RELEASED, body)
                assertEquals("session", chats.last().first)
                assertTrue(chats.last().second.contains(body))
                val previous = chats.size
                panel.mouse(MouseEvent.MOUSE_RELEASED, panel.pointAt(42))
                assertEquals(previous + if (state == PetState.DONE) 0 else 1, chats.size)
                panel.mouse(MouseEvent.MOUSE_MOVED, body)
                panel.mouse(MouseEvent.MOUSE_RELEASED, panel.pointAt(125))
                assertEquals(previous + if (state == PetState.DONE) 1 else 2, chats.size)
            }
            assertEquals(listOf("session"), finished)
            assertTrue("Quick chat must not also navigate to the tool window", opened.isEmpty())
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun `drag context menu empty space and removed pets never open quick chat`() = atBothScales {
        var chats = 0
        var menus = 0
        val panel = PetOverlay(PetSprites.load(), {}, onQuickChat = { _, _ -> chats++ }) { _, _, _ -> menus++ }
        try {
            panel.setSize(JBUI.scale(600), panel.overlayHeight)
            panel.update(listOf(PetSession("session", "Task", PetState.DONE)), false, true, false)
            val body = panel.pointAt(80)
            panel.mouse(MouseEvent.MOUSE_PRESSED, body)
            val drop = Point(body.x, body.y - JBUI.scale(30))
            panel.mouse(MouseEvent.MOUSE_DRAGGED, drop)
            panel.mouse(MouseEvent.MOUSE_RELEASED, drop)
            panel.mouse(MouseEvent.MOUSE_RELEASED, drop, MouseEvent.BUTTON3)
            assertEquals(1, menus)
            panel.mouse(MouseEvent.MOUSE_RELEASED, Point())
            panel.update(emptyList(), false, true, false)
            panel.mouse(MouseEvent.MOUSE_RELEASED, drop)
            assertEquals(0, chats)
        } finally {
            panel.dispose()
        }
    }

    @Test
    fun `demo clicks keep cycling instead of opening a real chat`() = atBothScales {
        val opened = mutableListOf<String>()
        var chats = 0
        val panel = PetOverlay(PetSprites.load(), opened::add, onQuickChat = { _, _ -> chats++ }) { _, _, _ -> }
        try {
            panel.setSize(JBUI.scale(600), panel.overlayHeight)
            panel.update(listOf(PetSession("demo", "Demo", PetState.WAITING)), true, true, false)
            panel.mouse(MouseEvent.MOUSE_RELEASED, panel.pointAt(80))
            assertEquals(listOf("demo"), opened)
            assertEquals(0, chats)
        } finally {
            panel.dispose()
        }
    }

    private fun atBothScales(test: () -> Unit) {
        val originalScale = JBUIScale.scale(1.0f)
        try {
            for (scale in listOf(1.0f, 2.0f)) SwingUtilities.invokeAndWait {
                JBUIScale.setUserScaleFactorForTest(scale)
                test()
            }
        } finally {
            SwingUtilities.invokeAndWait { JBUIScale.setUserScaleFactorForTest(originalScale) }
        }
    }

    private fun PetOverlay.pointAt(row: Int): Point {
        val y = JBUI.scale(row)
        val xs = (0 until width).filter { contains(it, y) }
        assertTrue("No interactive pet at row $row", xs.isNotEmpty())
        return Point(xs.average().toInt(), y)
    }

    private fun PetOverlay.mouse(type: Int, point: Point, button: Int = MouseEvent.BUTTON1) {
        val modifiers = if (type == MouseEvent.MOUSE_PRESSED || type == MouseEvent.MOUSE_DRAGGED) InputEvent.BUTTON1_DOWN_MASK else 0
        dispatchEvent(MouseEvent(this, type, 0, modifiers, point.x, point.y, 1, button == MouseEvent.BUTTON3, if (type == MouseEvent.MOUSE_DRAGGED) MouseEvent.NOBUTTON else button))
    }
}