package info.chrzanowski.airpets.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionWorkingTimeTest {
    private var now = 0L
    private val time = SessionWorkingTime { now }
    private val session = PetSession("one", "Task", PetState.WORKING, updatedAtMs = Long.MAX_VALUE)

    @Test
    fun `time starts from observation not session update timestamps`() {
        now = 100_000
        assertNull(time.elapsedMs(session.id))
        time.observe(listOf(session))
        assertEquals(0L, time.elapsedMs(session.id))
        now += 1_234
        assertEquals(1_234L, time.elapsedMs(session.id))
        assertNull(time.elapsedMs("unknown"))
    }

    @Test
    fun `each nonworking state pauses time and resuming accumulates prior work`() {
        PetState.entries.filter { it != PetState.WORKING }.forEach { paused ->
            val tracker = SessionWorkingTime { now }
            tracker.observe(listOf(session))
            now += 2_000
            tracker.observe(listOf(session.copy(state = paused)))
            now += 10_000
            assertEquals("$paused must not count as work", 2_000L, tracker.elapsedMs(session.id))
            tracker.observe(listOf(session))
            now += 3_000
            assertEquals(5_000L, tracker.elapsedMs(session.id))
        }
    }

    @Test
    fun `refreshes and title or cost updates do not reset or double count time`() {
        time.observe(listOf(session))
        now += 1_000
        time.observe(listOf(session.copy(title = "Renamed")))
        time.observe(listOf(session.copy(updatedAtMs = 0)))
        now += 1_000
        assertEquals(2_000L, time.elapsedMs(session.id))
        assertEquals("Reading must not mutate the counter", 2_000L, time.elapsedMs(session.id))
    }

    @Test
    fun `sessions have independent clocks and unseen completed work is unknown not zero`() {
        val second = session.copy(id = "two", state = PetState.DONE)
        time.observe(listOf(session, second))
        now += 1_000
        assertNull(time.elapsedMs(second.id))
        time.observe(listOf(session, second.copy(state = PetState.WORKING)))
        now += 2_000
        assertEquals(3_000L, time.elapsedMs(session.id))
        assertEquals(2_000L, time.elapsedMs(second.id))
    }

    @Test
    fun `disappearance and connection loss stop counting until observation resumes`() {
        time.observe(listOf(session))
        now += 2_000
        time.observe(emptyList())
        now += 50_000
        assertEquals(2_000L, time.elapsedMs(session.id))
        time.observe(listOf(session))
        now += 1_000
        assertEquals(3_000L, time.elapsedMs(session.id))
    }

    @Test
    fun `new project observation does not invent a historical total`() {
        time.observe(listOf(session))
        now += 5_000
        val reopened = SessionWorkingTime { now }
        reopened.observe(listOf(session))
        assertEquals(0L, reopened.elapsedMs(session.id))
    }
}