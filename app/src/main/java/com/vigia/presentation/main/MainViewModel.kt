package com.vigia.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vigia.di.appContainer
import com.vigia.domain.model.Alert
import com.vigia.domain.repository.RobotRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// Lo que se ve en todas las pestañas, como el contador de alertas de la barra inferior
data class MainUiState(val unacknowledgedAlerts: Int = 0)

class MainViewModel(repository: RobotRepository) : ViewModel() {

    val state: StateFlow<MainUiState> = repository.alerts
        .map(::mainState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mainState(repository.alerts.value))

    companion object {
        val Factory = viewModelFactory {
            initializer { MainViewModel(appContainer.robotRepository) }
        }
    }
}

private fun mainState(alerts: List<Alert>) = MainUiState(unacknowledgedAlerts = alerts.count { !it.acknowledged })
