package com.vigia.domain.rules

enum class BatteryLevel { OK, LOW, CRITICAL }

object BatteryRules {
    const val LOW = 25
    const val CRITICAL = 15
    const val MIN_TO_START = 30
    const val MIN_TO_RESUME = 15

    // Advertencia al bajar del 25 %, crítica al bajar del 15 %
    fun level(percent: Int): BatteryLevel = when {
        percent < CRITICAL -> BatteryLevel.CRITICAL
        percent < LOW -> BatteryLevel.LOW
        else -> BatteryLevel.OK
    }

    fun canStart(percent: Int): Boolean = percent >= MIN_TO_START

    fun canResume(percent: Int): Boolean = percent >= MIN_TO_RESUME
}
