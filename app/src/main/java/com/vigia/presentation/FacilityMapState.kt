package com.vigia.presentation

import com.vigia.domain.model.Pose

// Lo que dibuja el plano: ruta activa, puntos verificados y robot
data class FacilityMapState(
    val routeId: String? = null,
    val verifiedIds: Set<String> = emptySet(),
    val currentCheckpointId: String? = null,
    val robot: Pose? = null,
    val stale: Boolean = false,
)
