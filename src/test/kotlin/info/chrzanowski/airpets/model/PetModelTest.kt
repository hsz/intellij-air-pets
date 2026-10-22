package info.chrzanowski.airpets.model

import org.junit.Assert.*
import org.junit.Test

class PetModelTest {
    @Test
    fun `attention precedes work and rest when space is limited`() {
        val sessions = listOf(
            PetSession("idle", "Idle", PetState.RESTING, 100),
            PetSession("working", "Working", PetState.WORKING, 200),
            PetSession("waiting", "Waiting", PetState.WAITING, 1),
            PetSession("failed", "Failed", PetState.ERROR, 2),
        )
        assertEquals(listOf("waiting", "failed"), visibleSessions(sessions, 2).map { it.id })
        assertTrue(visibleSessions(sessions, 0).isEmpty())
        assertTrue(visibleSessions(sessions, -1).isEmpty())
    }

    @Test
    fun `selection is deterministic with no duplicate identities`() {
        val a = PetSession("a", "A", PetState.WORKING, 1)
        val b = PetSession("b", "B", PetState.WORKING, 2)
        assertEquals(listOf(b, a), visibleSessions(listOf(a, a, b), 12))
        assertEquals(visibleSessions(listOf(a, b), 12), visibleSessions(listOf(b, a), 12))
    }

    @Test
    fun `species is stable and safe for negative hashes`() {
        listOf("", "session-42", "polygenelubricants", "another workspace/session").forEach {
            assertEquals(PetKind.forSession(it), PetKind.forSession(it))
            assertTrue(PetKind.forSession(it) in PetKind.entries)
        }
    }

    @Test
    fun `all species are assigned and mascots use smooth artwork`() {
        assertEquals(listOf("snake", "rubber-duck", "kodee", "runzo", "clippy", "cat", "dog"), PetKind.entries.map { it.resourceName })
        assertEquals(PetKind.entries.toSet(), (0..100).map { PetKind.forSession("session-$it") }.toSet())
        assertEquals(listOf("snake", "rubber-duck", "cat", "dog"), PetKind.entries.filter { it.pixelArt }.map { it.resourceName })
        assertEquals(PetKind.entries.toSet(), demoSessions().map { PetKind.forSession(it.id) }.toSet())
    }

    @Test
    fun `walk stays within lane and reverses at its edges`() {
        val motion = PetMotion("a")
        val directions = mutableSetOf<Boolean>()
        repeat(2000) {
            motion.advance(0.1, 120.0, PetState.WORKING)
            assertTrue(motion.position in 0.0..1.0)
            directions += motion.facingRight
        }
        assertEquals(setOf(true, false), directions)
    }

    @Test
    fun `resting and input requests stay put and time jumps are bounded`() {
        val motion = PetMotion("stationary")
        val position = motion.position
        listOf(PetState.RESTING, PetState.WAITING, PetState.ERROR, PetState.DONE).forEach {
            motion.advance(1.0, 100.0, it)
            assertEquals(position, motion.position, 0.0)
        }
        motion.advance(100.0, 100.0, PetState.WORKING)
        assertTrue(kotlin.math.abs(motion.position - position) <= 0.024001)
        val after = motion.position
        motion.advance(0.1, 0.0, PetState.WORKING)
        motion.advance(-1.0, 100.0, PetState.WORKING)
        assertEquals(after, motion.position, 0.0)
    }

    @Test
    fun `demo covers every state with unique stable identities`() {
        assertEquals(PetState.entries.toSet(), demoSessions().map { it.state }.toSet())
        assertEquals(demoSessions().size, demoSessions().map { it.id }.toSet().size)
        assertEquals(demoSessions(), demoSessions())
    }
}