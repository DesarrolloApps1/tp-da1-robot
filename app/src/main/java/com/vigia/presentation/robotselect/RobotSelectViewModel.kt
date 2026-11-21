package com.vigia.presentation.robotselect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vigia.di.appContainer
import com.vigia.domain.model.RobotSummary
import com.vigia.domain.repository.RobotRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class RobotSelectUiState(val robots: List<RobotSummary> = emptyList())

class RobotSelectViewModel(private val repository: RobotRepository) : ViewModel() {

    val state: StateFlow<RobotSelectUiState> = repository.robots
        .map(::RobotSelectUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RobotSelectUiState(repository.robots.value))

    fun onSelect(robotId: String) {
        repository.select(robotId)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { RobotSelectViewModel(appContainer.robotRepository) }
        }
    }
}
