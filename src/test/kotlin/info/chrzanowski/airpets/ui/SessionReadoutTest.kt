package info.chrzanowski.airpets.ui

import info.chrzanowski.airpets.model.PetSession
import info.chrzanowski.airpets.model.PetState
import info.chrzanowski.airpets.model.SessionCost
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.*

class SessionReadoutTest {
    @Test
    fun `cost and time can be shown together or separately without empty separators`() {
        val session = PetSession(
            "one",
            "Task",
            PetState.WORKING,
            cost = SessionCost(BigDecimal("0.42"), true),
            observedWorkingMs = 83_000,
        )
        assertEquals("", sessionReadout(session, cost = false, duration = false))
        assertEquals("≈$0.42", sessionReadout(session, cost = true, duration = false))
        assertEquals("1m 23s", sessionReadout(session, cost = false, duration = true))
        assertEquals("≈$0.42 · 1m 23s", sessionReadout(session, cost = true, duration = true))
        assertEquals("1m 23s", sessionReadout(session.copy(cost = null), cost = true, duration = true))
        assertEquals(
            "",
            sessionReadout(session.copy(cost = null, observedWorkingMs = null), cost = true, duration = true),
        )
        val tooltip = sessionMetricsTooltip(session, cost = true, duration = true)
        assertTrue(tooltip.contains("Estimated cost (USD)"))
        assertTrue(tooltip.contains("Observed working time"))
        assertTrue(tooltip.contains("not historical total"))
        assertEquals("", sessionMetricsTooltip(session, cost = false, duration = false))
        val unknown = session.copy(cost = null, observedWorkingMs = null)
        assertTrue(sessionMetricsTooltip(unknown, cost = true, duration = true).contains("Cost not reported by AIR"))
        assertTrue(sessionMetricsTooltip(unknown, cost = true, duration = true).contains("session not yet observed working"))
    }

    @Test
    fun `USD costs retain precision distinguish estimates and do not claim tiny costs are free`() {
        assertEquals("$0.00", sessionCostText(SessionCost(BigDecimal.ZERO)))
        assertEquals("<$0.01", sessionCostText(SessionCost(BigDecimal("0.000001"))))
        assertEquals("≈<$0.01", sessionCostText(SessionCost(BigDecimal("0.005"), estimated = true)))
        assertEquals("$0.01", sessionCostText(SessionCost(BigDecimal("0.01"))))
        assertEquals("≈$1.24", sessionCostText(SessionCost(BigDecimal("1.235"), estimated = true)))
        assertEquals("$1234567890.12", sessionCostText(SessionCost(BigDecimal("1234567890.123456789"))))
    }

    @Test
    fun `USD currency is independent of the IDE locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("$1.23", sessionCostText(SessionCost(BigDecimal("1.23"))))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `unknown and invalid metrics are not rendered as zero`() {
        assertNull(sessionCostText(null))
        assertNull(sessionCostText(SessionCost(BigDecimal("-1"))))
        assertNull(sessionDurationText(null))
        assertNull(sessionDurationText(-1))
    }

    @Test
    fun `duration stays compact at second minute hour and day boundaries`() {
        val cases = mapOf(
            0L to "0s",
            999L to "0s",
            59_999L to "59s",
            60_000L to "1m 0s",
            3_599_000L to "59m 59s",
            3_600_000L to "1h 0m",
            90_061_000L to "1d 1h",
        )
        cases.forEach { (duration, expected) -> assertEquals(expected, sessionDurationText(duration)) }
        assertNotNull(sessionDurationText(Long.MAX_VALUE))
    }
}