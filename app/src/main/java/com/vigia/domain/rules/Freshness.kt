package com.vigia.domain.rules

object Freshness {
    const val STALE_AFTER_MS = 3_000L

    // Sin ningún dato también cuenta como antiguo
    fun isStale(lastUpdateMillis: Long?, now: Long): Boolean =
        lastUpdateMillis == null || now - lastUpdateMillis > STALE_AFTER_MS

    fun ageSeconds(lastUpdateMillis: Long?, now: Long): Long? =
        lastUpdateMillis?.let { ((now - it) / 1_000).coerceAtLeast(0) }
}
