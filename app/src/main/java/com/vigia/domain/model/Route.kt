package com.vigia.domain.model

data class Route(
    val id: String,
    val name: String,
    val checkpointIds: List<String>,
)
