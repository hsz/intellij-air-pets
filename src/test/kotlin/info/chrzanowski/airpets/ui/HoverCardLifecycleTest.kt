package info.chrzanowski.airpets.ui

import org.junit.Assert.*
import org.junit.Test

class HoverCardLifecycleTest {
    private data class Pending(val due: Long, val action: () -> Unit, var cancelled: Boolean = false)

    private var now = 0L
    private val pending = mutableListOf<Pending>()
    private val shown = mutableListOf<String>()
    private var closed = 0
    private val lifecycle = HoverCardLifecycle(shown::add, { closed++ }) { delay, action ->
        val task = Pending(now + delay, action)
        pending += task
        val cancel: () -> Unit = { task.cancelled = true }
        cancel
    }

    @Test
    fun `brief hover never opens and moving within a pet does not restart delay`() {
        lifecycle.hover("one")
        advance(300)
        lifecycle.hover("one")
        advance(149)
        assertTrue(shown.isEmpty())
        advance(1)
        assertEquals(listOf("one"), shown)
        assertTrue(lifecycle.visible)
        lifecycle.cancel()
        lifecycle.hover("two")
        advance(400)
        lifecycle.hover(null)
        advance(1000)
        assertEquals(listOf("one"), shown)
        assertNull(lifecycle.sessionId)
    }

    @Test
    fun `cursor can cross gap and use card children without dismissing it`() {
        lifecycle.hover("one")
        advance(450)
        lifecycle.hover(null)
        advance(200)
        lifecycle.enterCard()
        advance(1000)
        assertTrue(lifecycle.visible)
        lifecycle.leaveCard()
        advance(100)
        lifecycle.enterCard()
        advance(1000)
        assertTrue(lifecycle.visible)
        lifecycle.leaveCard()
        advance(249)
        assertTrue(lifecycle.visible)
        advance(1)
        assertFalse(lifecycle.visible)
        assertNull(lifecycle.sessionId)
    }

    @Test
    fun `returning to pet cancels dismissal and switching pets has a fresh delay`() {
        lifecycle.hover("one")
        advance(450)
        lifecycle.hover(null)
        advance(200)
        lifecycle.hover("one")
        advance(1000)
        assertTrue(lifecycle.visible)
        lifecycle.hover("two")
        assertFalse(lifecycle.visible)
        assertEquals("two", lifecycle.sessionId)
        advance(449)
        assertEquals(listOf("one"), shown)
        advance(1)
        assertEquals(listOf("one", "two"), shown)
    }

    @Test
    fun `cancellation discards pending show and close callbacks`() {
        lifecycle.hover("one")
        advance(100)
        lifecycle.cancel()
        advance(1000)
        assertTrue(shown.isEmpty())
        lifecycle.hover("two")
        advance(450)
        lifecycle.hover(null)
        lifecycle.cancel()
        val closures = closed
        advance(1000)
        assertEquals(closures, closed)
        assertFalse(lifecycle.visible)
        assertNull(lifecycle.sessionId)
    }

    private fun advance(ms: Long) {
        now += ms
        while (true) {
            val task = pending.filter { !it.cancelled && it.due <= now }.minByOrNull { it.due } ?: break
            pending.remove(task)
            task.action()
        }
    }
}