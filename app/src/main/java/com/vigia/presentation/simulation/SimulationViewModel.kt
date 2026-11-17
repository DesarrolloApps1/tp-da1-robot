package com.vigia.presentation.simulation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vigia.di.appContainer
import com.vigia.domain.repository.RobotRepository
import com.vigia.domain.repository.SimulationControls
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SimulationViewModel(private val controls: SimulationControls, robot: RobotRepository) : ViewModel() {

    val state: StateFlow<SimulationUiState> = combine(
        controls.connected,
        controls.speedFactor,
        robot.telemetry,
        ::simulationState,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        simulationState(controls.connected.value, controls.speedFactor.value, robot.telemetry.value),
    )

    fun onConnectedChange(connected: Boolean) = controls.setConnected(connected)

    fun onRejectNext() = controls.rejectNextCommand()

    fun onBattery(percent: Int) = controls.setBattery(percent.coerceIn(0, 100))

    fun onObstacle() = controls.triggerObstacle()

    fun onPerson() = controls.triggerPersonInRestrictedZone()

    fun onOpenDoor() = controls.triggerOpenDoor()

    fun onSpeed(factor: Int) = controls.setSpeedFactor(factor)

    companion object {
        val SPEEDS = listOf(1, 5)

        val Factory = viewModelFactory {
            initializer { SimulationViewModel(appContainer.simulationControls, appContainer.robotRepository) }
        }
    }
}
