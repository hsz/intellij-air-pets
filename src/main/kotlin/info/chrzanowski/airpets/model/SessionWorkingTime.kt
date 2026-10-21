package info.chrzanowski.airpets.model

internal class SessionWorkingTime(private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val entries = mutableMapOf<String, Entry>()

    fun observe(sessions: List<PetSession>) {
        val now = clockMs()
        val working = sessions.filter { it.state == PetState.WORKING }.map { it.id }.toSet()
        entries.forEach { (id, entry) ->
            entry.totalMs = entry.elapsedAt(now)
            entry.startedAtMs = now.takeIf { id in working }
        }
        working.forEach { entries.getOrPut(it) { Entry(0, now) } }
    }

    fun elapsedMs(id: String): Long? = entries[id]?.elapsedAt(clockMs())

    private class Entry(var totalMs: Long, var startedAtMs: Long?) {
        fun elapsedAt(now: Long): Long {
            val delta = startedAtMs?.let { (now - it).coerceAtLeast(0) } ?: 0
            return totalMs + delta.coerceAtMost(Long.MAX_VALUE - totalMs)
        }
    }
}