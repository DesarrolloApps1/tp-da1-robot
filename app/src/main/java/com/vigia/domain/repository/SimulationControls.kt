package com.vigia.domain.repository

import kotlinx.coroutines.flow.StateFlow

// Controles del panel de simulación; la UI depende de esta interfaz y no del simulador
interface SimulationControls {
    val speedFactor: StateFlow<Int>
    val connected: StateFlow<Boolean>

    fun setConnected(connected: Boolean)

    fun rejectNextCommand()

    fun setBattery(percent: Int)

    fun triggerObstacle()

    fun triggerPersonInRestrictedZone()

    fun triggerOpenDoor()

    fun setSpeedFactor(factor: Int)
}
