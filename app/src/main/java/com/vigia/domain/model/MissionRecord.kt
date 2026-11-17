package com.vigia.domain.model

enum class MissionResult { IN_PROGRESS, COMPLETED, CANCELLED, RETURNED_TO_BASE, ABORTED }

data class MissionRecord(
    val id: String,
    val routeId: String,
    val routeName: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val result: MissionResult,
    val checkpointsVerified: Int,
    val checkpointsTotal: Int,
    val peopleDetected: Int,
    val openDoors: Int,
)
