package com.vigia.domain.model

data class RobotSummary(
    val id: String,
    val name: String,
    val model: String,
    val available: Boolean,
    val note: String,
)
