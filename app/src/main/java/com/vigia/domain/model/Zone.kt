package com.vigia.domain.model

// Rectángulo del plano en metros; los bordes cuentan como parte de la zona
data class Zone(
    val name: String,
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    val restricted: Boolean,
) {
    fun contains(x: Double, y: Double): Boolean = x in left..right && y in top..bottom
}
