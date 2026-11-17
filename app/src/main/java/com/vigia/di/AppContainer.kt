package com.vigia.di

import android.content.Context
import androidx.room.Room
import com.vigia.data.local.RoomHistoryRepository
import com.vigia.data.local.VigiaDatabase
import com.vigia.data.sim.RobotSimulator
import com.vigia.data.sim.SimulatedRobotRepository
import com.vigia.domain.model.SystemClock
import com.vigia.domain.repository.HistoryRepository
import com.vigia.domain.repository.RobotRepository
import com.vigia.domain.repository.SimulationControls
import com.vigia.domain.usecase.HistoryRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Dependencias de la app; se crea una sola vez en VigiaApplication
class AppContainer(context: Context) {

    // Vive mientras viva la app: el simulador y el historial siguen corriendo entre pantallas
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val simulatedRobot = SimulatedRobotRepository(RobotSimulator(SystemClock), appScope, SystemClock)

    val robotRepository: RobotRepository = simulatedRobot
    val simulationControls: SimulationControls = simulatedRobot

    private val database = Room.databaseBuilder(context.applicationContext, VigiaDatabase::class.java, VigiaDatabase.NAME)
        .build()

    val historyRepository: HistoryRepository = RoomHistoryRepository(database)

    init {
        // Arranca ya: los eventos del robot no se repiten para quien se suscribe tarde
        HistoryRecorder(robotRepository, historyRepository, sessionId = SystemClock.now().toString(36))
            .start(appScope)
    }
}
