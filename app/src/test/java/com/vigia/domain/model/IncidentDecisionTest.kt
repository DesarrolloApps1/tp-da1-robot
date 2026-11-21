package com.vigia.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentDecisionTest {

    @Test
    fun `options depend on the alert kind`() {
        assertEquals(
            listOf(IncidentDecision.WAIT, IncidentDecision.SKIP_CHECKPOINT, IncidentDecision.RETURN_TO_BASE),
            IncidentDecision.optionsFor(AlertKind.OBSTACLE),
        )
        assertEquals(
            listOf(IncidentDecision.INTRUDER, IncidentDecision.AUTHORIZED_EMPLOYEE, IncidentDecision.FALSE_ALARM),
            IncidentDecision.optionsFor(AlertKind.PERSON_IN_RESTRICTED_ZONE),
        )
        assertTrue(IncidentDecision.optionsFor(AlertKind.DOOR_OPEN).isEmpty())
    }
}
