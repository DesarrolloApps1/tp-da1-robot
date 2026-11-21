package com.vigia.domain.model

import kotlin.math.hypot

// Plano simplificado de la planta baja (40 x 20 m) que usan el simulador y el mapa
object Facility {
    const val WIDTH_METERS = 40.0
    const val HEIGHT_METERS = 20.0
    const val OUTSIDE = "Fuera del plano"
    const val BASE_ID = "BASE"

    val checkpoints: List<Checkpoint> = listOf(
        Checkpoint(BASE_ID, "Base de carga", CheckpointType.SAFE_POINT, 3.0, 3.0),
        Checkpoint("CP1", "Hall de entrada", CheckpointType.DOOR, 8.0, 2.0),
        Checkpoint("CP2", "Molinetes", CheckpointType.PASSAGE, 12.0, 5.0),
        Checkpoint("CP3", "Pasillo de aulas A", CheckpointType.PASSAGE, 20.0, 5.0),
        Checkpoint("CP4", "Laboratorio de informática", CheckpointType.RESTRICTED, 30.0, 4.0),
        Checkpoint("CP5", "Biblioteca", CheckpointType.PASSAGE, 34.0, 12.0),
        Checkpoint("CP6", "Sala técnica", CheckpointType.RESTRICTED, 30.0, 17.0),
        Checkpoint("CP7", "Salida de emergencia", CheckpointType.DOOR, 20.0, 18.0),
        Checkpoint("CP8", "Pasillo de aulas B", CheckpointType.PASSAGE, 10.0, 14.0),
    )

    // Donde dos zonas se superponen gana la primera de la lista
    val zones: List<Zone> = listOf(
        Zone("Central", 0.0, 0.0, 6.0, 6.0, restricted = false),
        Zone("Hall", 6.0, 0.0, 14.0, 8.0, restricted = false),
        Zone("Pasillo A", 14.0, 3.0, 26.0, 7.0, restricted = false),
        Zone("Aulas", 14.0, 7.0, 26.0, 12.0, restricted = false),
        Zone("Laboratorio", 26.0, 0.0, 36.0, 8.0, restricted = true),
        Zone("Biblioteca", 30.0, 8.0, 40.0, 16.0, restricted = false),
        Zone("Sala técnica", 26.0, 14.0, 34.0, 20.0, restricted = true),
        Zone("Pasillo B", 6.0, 12.0, 26.0, 16.0, restricted = false),
        Zone("Salida de emergencia", 16.0, 16.0, 24.0, 20.0, restricted = false),
    )

    val routes: List<Route> = listOf(
        Route(
            "ronda-completa",
            "Ronda completa",
            listOf(BASE_ID, "CP1", "CP2", "CP3", "CP4", "CP5", "CP6", "CP7", "CP8", BASE_ID),
        ),
        Route("ronda-perimetral", "Ronda perimetral", listOf(BASE_ID, "CP1", "CP7", "CP2", BASE_ID)),
        Route("zonas-criticas", "Zonas críticas", listOf(BASE_ID, "CP4", "CP6", BASE_ID)),
    )

    fun checkpoint(id: String): Checkpoint? = checkpoints.firstOrNull { it.id == id }

    fun route(id: String): Route? = routes.firstOrNull { it.id == id }

    fun zoneAt(x: Double, y: Double): String = zones.firstOrNull { it.contains(x, y) }?.name ?: OUTSIDE

    // Suma de tramos en línea recta entre puntos consecutivos
    fun routeLengthMeters(route: Route): Double =
        route.checkpointIds
            .mapNotNull { checkpoint(it) }
            .zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }
            .sum()
}
