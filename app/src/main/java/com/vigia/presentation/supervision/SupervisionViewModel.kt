package com.vigia.presentation.supervision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vigia.di.appContainer
import com.vigia.domain.model.Clock
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.IncidentDecision
import com.vigia.domain.model.SystemClock
import com.vigia.domain.repository.RobotRepository
import com.vigia.presentation.resolveIncidentParams
import com.vigia.presentation.ticker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SupervisionViewModel(
    private val repository: RobotRepository,
    private val clock: Clock = SystemClock,
    tickMillis: Long = 1_000,
) : ViewModel() {

    val state: StateFlow<SupervisionUiState> = combine(
        repository.telemetry,
        repository.lastUpdateMillis,
        repository.alerts,
        repository.commands,
        ticker(tickMillis),
    ) { telemetry, lastUpdate, alerts, commands, _ ->
        supervisionState(telemetry, lastUpdate, alerts, commands, clock.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupervisionUiState.Initial)

    fun onCommand(type: CommandType, params: Map<String, String> = emptyMap()) {
        viewModelScope.launch { repository.send(type, params) }
    }

    fun onDecide(alertId: String, decision: IncidentDecision) {
        onCommand(CommandType.RESOLVE_INCIDENT, resolveIncidentParams(alertId, decision))
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SupervisionViewModel(appContainer.robotRepository) }
        }
    }
}
