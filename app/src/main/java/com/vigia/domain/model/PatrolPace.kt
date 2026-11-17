package com.vigia.domain.model

// Ritmo de la ronda: lo usan el simulador y la duración estimada de cada ruta
object PatrolPace {
    // Velocidad limitada por seguridad (PLAN.md, sección 7)
    const val SPEED_MPS = 0.8

    // Tiempo detenido en cada punto de control para verificarlo
    const val DWELL_SECONDS = 3.0

    fun checkpointsToVerify(route: Route): Int = route.checkpointIds.count { it != Facility.BASE_ID }

    fun estimatedSeconds(route: Route): Double =
        Facility.routeLengthMeters(route) / SPEED_MPS + checkpointsToVerify(route) * DWELL_SECONDS
}
