package com.vigia.presentation.missions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vigia.di.appContainer
import com.vigia.domain.model.Clock
import com.vigia.domain.model.CommandStatus
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.SystemClock
import com.vigia.domain.repository.RobotRepository
import com.vigia.presentation.ticker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MissionsViewModel(
    private val repository: RobotRepository,
    private val clock: Clock = SystemClock,
    tickMillis: Long = 1_000,
) : ViewModel() {

    private val startConfirmed = MutableStateFlow(false)

    val state: StateFlow<MissionsUiState> = combine(
        repository.telemetry,
        repository.lastUpdateMillis,
        repository.alerts,
        repository.commands,
        ticker(tickMillis),
    ) { telemetry, lastUpdate, alerts, commands, _ ->
        missionsState(telemetry, lastUpdate, alerts, commands, clock.now())
    }.combine(startConfirmed) { state, confirmed ->
        state.copy(startConfirmed = confirmed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MissionsUiState.Initial)

    fun onStart(routeId: String) {
        viewModelScope.launch {
            val record = repository.send(CommandType.START_MISSION, startMissionParams(routeId))
            if (record.status == CommandStatus.CONFIRMED) startConfirmed.value = true
        }
    }

    // La pantalla ya navegó: no volver a hacerlo al regresar a esta pestaña
    fun onStartHandled() {
        startConfirmed.value = false
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { MissionsViewModel(appContainer.robotRepository) }
        }
    }
}
