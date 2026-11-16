package com.vigia.ui.navigation

import kotlinx.serialization.Serializable

// Selección de robot y, adentro, las cuatro pestañas de la central
sealed interface Route {
    @Serializable data object RobotSelect : Route
    @Serializable data object Main : Route
    @Serializable data object Supervision : Route
    @Serializable data object Missions : Route
    @Serializable data object Alerts : Route
    @Serializable data object History : Route
}
