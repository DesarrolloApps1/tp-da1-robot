package com.vigia.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vigia.di.appContainer
import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.EventType
import com.vigia.domain.repository.HistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val history: HistoryRepository) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val expandedId = MutableStateFlow<String?>(null)

    // El id viaja con sus eventos para no mostrar la línea de tiempo de la misión anterior
    private val expanded = expandedId.flatMapLatest { id ->
        if (id == null) flowOf(null to emptyList()) else history.eventsFor(id).map { id to it }
    }

    private val incidentMissionIds = history.events.map { events ->
        events.filter { it.type == EventType.ALERT }.mapNotNullTo(mutableSetOf(), EventRecord::missionId)
    }

    val state: StateFlow<HistoryUiState> = combine(
        history.missions,
        incidentMissionIds,
        filter,
        expanded,
    ) { missions, incidentIds, filter, (id, events) ->
        historyState(missions, incidentIds, filter, id, events)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onToggle(missionId: String) {
        expandedId.update { if (it == missionId) null else missionId }
    }

    fun onFilter(value: HistoryFilter) {
        filter.value = value
    }

    fun onClear() {
        expandedId.value = null
        viewModelScope.launch { history.clear() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { HistoryViewModel(appContainer.historyRepository) }
        }
    }
}
