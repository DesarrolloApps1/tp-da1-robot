package com.vigia.presentation.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vigia.di.appContainer
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.IncidentDecision
import com.vigia.domain.repository.RobotRepository
import com.vigia.presentation.ackAlertParams
import com.vigia.presentation.resolveIncidentParams
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlertsViewModel(private val repository: RobotRepository) : ViewModel() {

    val state: StateFlow<AlertsUiState> = combine(
        repository.telemetry,
        repository.alerts,
        repository.commands,
    ) { telemetry, alerts, commands ->
        alertsState(telemetry?.state, alerts, commands)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsUiState())

    fun onAcknowledge(alertId: String) {
        viewModelScope.launch { repository.send(CommandType.ACK_ALERT, ackAlertParams(alertId)) }
    }

    fun onDecide(alertId: String, decision: IncidentDecision) {
        viewModelScope.launch { repository.send(CommandType.RESOLVE_INCIDENT, resolveIncidentParams(alertId, decision)) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AlertsViewModel(appContainer.robotRepository) }
        }
    }
}
