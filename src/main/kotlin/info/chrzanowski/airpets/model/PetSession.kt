package info.chrzanowski.airpets.model

import java.math.BigDecimal

enum class PetState(val label: String, val badge: String, val priority: Int) {
    WAITING("Needs your input", "?", 0),
    ERROR("Stopped with an error", "!", 1),
    WORKING("Working", "…", 2),
    REVIEW("Ready for review", "✓", 3),
    DONE("Completed", "♥", 4),
    RESTING("Ready / paused", "zZ", 5),
}

data class PetSession(
    val id: String,
    val title: String,
    val state: PetState,
    val updatedAtMs: Long = 0,
    val detail: String = "",
    val cost: SessionCost? = null,
    val observedWorkingMs: Long? = null,
    val workspacePath: String? = null,
    val worktreeBranch: String? = null,
)

data class SessionCost(val amountUsd: BigDecimal, val estimated: Boolean = false)

class PetKind private constructor(val resourceName: String, val pixelArt: Boolean = true) {
    companion object {
        val entries: List<PetKind> = listOf(
            PetKind("snake"),
            PetKind("rubber-duck"),
            PetKind("kodee", pixelArt = false),
            PetKind("runzo", pixelArt = false),
            PetKind("clippy", pixelArt = false),
        )

        fun forSession(id: String): PetKind = entries[Math.floorMod(id.hashCode(), entries.size)]
    }
}

fun visibleSessions(sessions: List<PetSession>, capacity: Int): List<PetSession> = sessions
    .distinctBy { it.id }
    .sortedWith(compareBy<PetSession> { it.state.priority }.thenByDescending { it.updatedAtMs }.thenBy { it.id })
    .take(capacity.coerceAtLeast(0))

fun demoSessions(): List<PetSession> = listOf(
    PetSession(
        "demo-working",
        "Refactor the API",
        PetState.WORKING,
        detail = "Demo · running tools",
        cost = SessionCost(BigDecimal("0.42"), estimated = true),
        observedWorkingMs = 83_000,
        workspacePath = "/demo/air-pets",
        worktreeBranch = "refactor-api",
    ),
    PetSession(
        "demo-waiting",
        "Add dark mode",
        PetState.WAITING,
        detail = "Demo · waiting for your answer",
        cost = SessionCost(BigDecimal("0.12")),
        observedWorkingMs = 42_000,
    ),
    PetSession(
        "demo-review",
        "Write tests",
        PetState.REVIEW,
        detail = "Demo · review the changes",
        cost = SessionCost(BigDecimal("1.23")),
        observedWorkingMs = 241_000,
    ),
    PetSession(
        "demo-ready",
        "Investigate startup",
        PetState.RESTING,
        detail = "Demo · paused",
        cost = SessionCost(BigDecimal.ZERO),
        observedWorkingMs = 0,
    ),
    PetSession(
        "demo-completed",
        "Fix the build",
        PetState.DONE,
        detail = "Demo · completed",
        cost = SessionCost(BigDecimal("0.78")),
        observedWorkingMs = 125_000,
    ),
    PetSession(
        "demo-error",
        "Run deployment",
        PetState.ERROR,
        detail = "Demo · command failed",
        observedWorkingMs = 18_000,
    ),
)