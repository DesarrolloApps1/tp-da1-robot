package com.vigia.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryRulesTest {

    @Test
    fun `level changes exactly at 15 and 25`() {
        assertEquals(BatteryLevel.CRITICAL, BatteryRules.level(0))
        assertEquals(BatteryLevel.CRITICAL, BatteryRules.level(14))
        assertEquals(BatteryLevel.LOW, BatteryRules.level(15))
        assertEquals(BatteryLevel.LOW, BatteryRules.level(24))
        assertEquals(BatteryLevel.OK, BatteryRules.level(25))
        assertEquals(BatteryLevel.OK, BatteryRules.level(100))
    }

    @Test
    fun `start needs at least 30 percent`() {
        assertFalse(BatteryRules.canStart(29))
        assertTrue(BatteryRules.canStart(30))
    }

    @Test
    fun `resume needs at least 15 percent`() {
        assertFalse(BatteryRules.canResume(14))
        assertTrue(BatteryRules.canResume(15))
    }
}
