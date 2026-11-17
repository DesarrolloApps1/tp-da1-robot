package com.vigia.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshnessTest {

    @Test
    fun `no data is stale`() {
        assertTrue(Freshness.isStale(null, 10_000))
        assertNull(Freshness.ageSeconds(null, 10_000))
    }

    @Test
    fun `stale only after more than 3 seconds`() {
        assertFalse(Freshness.isStale(10_000, 10_000))
        assertFalse(Freshness.isStale(10_000, 13_000))
        assertTrue(Freshness.isStale(10_000, 13_001))
    }

    @Test
    fun `age is whole seconds and never negative`() {
        assertEquals(0L, Freshness.ageSeconds(10_000, 10_999))
        assertEquals(12L, Freshness.ageSeconds(10_000, 22_500))
        assertEquals(0L, Freshness.ageSeconds(10_000, 9_000))
    }
}
