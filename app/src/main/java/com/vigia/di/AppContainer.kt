package com.vigia.di

import android.content.Context
import com.vigia.data.sim.RobotSimulator
import com.vigia.data.sim.SimulatedRobotRepository
import com.vigia.domain.model.SystemClock
import com.vigia.domain.repository.RobotRepository
import com.vigia.domain.repository.SimulationControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Dependencias de la app; se completa con el historial
class AppContainer(@Suppress("unused") private val context: Context) {

    // Vive mientras viva la app: el simulador sigue corriendo entre pantallas
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val simulatedRobot = SimulatedRobotRepository(RobotSimulator(SystemClock), appScope, SystemClock)

    val robotRepository: RobotRepository = simulatedRobot
    val simulationControls: SimulationControls = simulatedRobot
}
