package info.chrzanowski.airpets.ui

import info.chrzanowski.airpets.model.PetSession
import info.chrzanowski.airpets.model.SessionCost
import java.math.BigDecimal
import java.math.RoundingMode

internal fun sessionReadout(session: PetSession, cost: Boolean, duration: Boolean): String = listOfNotNull(
    if (cost) sessionCostText(session.cost) else null,
    if (duration) sessionDurationText(session.observedWorkingMs) else null,
).joinToString(" · ")

internal fun sessionMetricsTooltip(session: PetSession, cost: Boolean, duration: Boolean): String = listOfNotNull(
    if (cost) sessionCostText(session.cost)?.let { "${if (session.cost?.estimated == true) "Estimated cost" else "Cost"} (USD): $it" }
        ?: "Cost not reported by AIR" else null,
    if (duration) sessionDurationText(session.observedWorkingMs)?.let { "Observed working time: $it (this project opening only; not historical total)" }
        ?: "Working time unavailable: session not yet observed working" else null,
).joinToString(" · ")

internal fun sessionCostText(cost: SessionCost?): String? {
    val amount = cost?.amountUsd?.takeIf { it.signum() >= 0 } ?: return null
    val value = if (amount.signum() > 0 && amount < BigDecimal("0.01")) {
        "<$0.01"
    } else {
        "$${amount.setScale(2, RoundingMode.HALF_UP).toPlainString()}"
    }
    return if (cost.estimated) "≈$value" else value
}

internal fun sessionDurationText(durationMs: Long?): String? {
    val seconds = durationMs?.takeIf { it >= 0 }?.div(1_000) ?: return null
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3_600 -> "${seconds / 60}m ${seconds % 60}s"
        seconds < 86_400 -> "${seconds / 3_600}h ${seconds % 3_600 / 60}m"
        else -> "${seconds / 86_400}d ${seconds % 86_400 / 3_600}h"
    }
}