package com.vigia.presentation.history

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.vigia.domain.model.AlertSeverity
import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.EventType
import com.vigia.domain.model.MissionRecord
import com.vigia.domain.model.MissionResult
import com.vigia.fakes.FakeHistoryRepository
import com.vigia.fakes.MainDispatcherRule
import com.vigia.presentation.StatusTone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun mission(
        id: String,
        startedAt: Long,
        endedAt: Long?,
        result: MissionResult,
        verified: Int = 8,
    ) = MissionRecord(id, "ronda-completa", "Ronda completa", startedAt, endedAt, result, verified, 8, 1, 2)

    // Lo más nuevo primero, como lo devuelve Room
    private val missions = listOf(
        mission("m-0002@s2", startedAt = 500_000, endedAt = null, result = MissionResult.IN_PROGRESS, verified = 3),
        mission("m-0001@s2", startedAt = 300_000, endedAt = 421_000, result = MissionResult.COMPLETED),
        mission("m-0002@s1", startedAt = 100_000, endedAt = null, result = MissionResult.ABORTED, verified = 4),
        mission("m-0001@s1", startedAt = 0, endedAt = 45_000, result = MissionResult.ABORTED, verified = 1),
    )

    private val history = FakeHistoryRepository(missions)

    private suspend fun event(missionId: String?, type: EventType, timestamp: Long, message: String) =
        history.addEvent(EventRecord(0, timestamp, missionId, type, AlertSeverity.INFO, message, zone = null))

    // Corre lo pendiente y devuelve el último estado emitido
    private suspend fun TestScope.latest(states: ReceiveTurbine<HistoryUiState>): HistoryUiState {
        runCurrent()
        return states.expectMostRecentItem()
    }

    private fun HistoryUiState.item(id: String) = missions.single { it.id == id }

    @Test
    fun mapsMissionsNewestFirstWithoutTheSessionInTheId() = runTest {
        HistoryViewModel(history).state.test {
            val state = latest(this)
            assertTrue(state.hasHistory)
            assertEquals(listOf("m-0002@s2", "m-0001@s2", "m-0002@s1", "m-0001@s1"), state.missions.map { it.id })

            val done = state.item("m-0001@s2")
            assertEquals("m-0001", done.displayId)
            assertEquals("Ronda completa", done.routeName)
            assertEquals(300_000L, done.startedAtMillis)
            assertEquals("2 min 1 s", done.durationText)
            assertEquals("Completada", done.resultLabel)
            assertEquals(StatusTone.OK, done.resultTone)
            assertEquals("8/8", done.verifiedText)
            assertEquals(1, done.peopleDetected)
            assertEquals(2, done.openDoors)

            val running = state.item("m-0002@s2")
            assertEquals("En curso", running.resultLabel)
            assertNull(running.durationText)

            // De una ejecución anterior que se cerró con la ronda en curso
            val interrupted = state.item("m-0002@s1")
            assertEquals("m-0002", interrupted.displayId)
            assertEquals("Interrumpida", interrupted.resultLabel)
            assertNull(interrupted.durationText)

            val aborted = state.item("m-0001@s1")
            assertEquals("Abortada", aborted.resultLabel)
            assertEquals("45 s", aborted.durationText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun incidentsFilterKeepsMissionsWithAlerts() = runTest {
        event("m-0001@s2", EventType.CHECKPOINT, 310_000, "Punto verificado: Hall de entrada")
        event("m-0002@s1", EventType.ALERT, 110_000, "Obstáculo bloquea el paso")
        event(null, EventType.ALERT, 200_000, "Batería baja")
        val viewModel = HistoryViewModel(history)
        viewModel.state.test {
            assertEquals(listOf(false, false, true, false), latest(this).missions.map { it.hasIncidents })

            viewModel.onFilter(HistoryFilter.WITH_INCIDENTS)
            val filtered = latest(this)
            assertEquals(HistoryFilter.WITH_INCIDENTS, filtered.filter)
            assertEquals(listOf("m-0002@s1"), filtered.missions.map { it.id })

            viewModel.onFilter(HistoryFilter.ALL)
            assertEquals(4, latest(this).missions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun tapLoadsTheTimelineOfTheStoredMissionOldestFirst() = runTest {
        event("m-0001@s2", EventType.MISSION, 300_000, "Misión m-0001 iniciada: Ronda completa")
        event("m-0001@s2", EventType.CHECKPOINT, 310_000, "Punto verificado: Hall de entrada")
        event("m-0001@s1", EventType.MISSION, 0, "Misión m-0001 iniciada: Ronda completa")
        val viewModel = HistoryViewModel(history)
        viewModel.state.test {
            assertTrue(latest(this).timeline.isEmpty())

            viewModel.onToggle("m-0001@s2")
            val open = latest(this)
            assertEquals("m-0001@s2", open.expandedId)
            assertEquals(listOf(300_000L, 310_000L), open.timeline.map { it.timestampMillis })
            assertEquals(listOf("Misión", "Punto de control"), open.timeline.map { it.typeLabel })

            viewModel.onToggle("m-0001@s2")
            val closed = latest(this)
            assertNull(closed.expandedId)
            assertTrue(closed.timeline.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearEmptiesTheHistory() = runTest {
        event("m-0001@s2", EventType.MISSION, 300_000, "Misión m-0001 iniciada: Ronda completa")
        val viewModel = HistoryViewModel(history)
        viewModel.state.test {
            latest(this)
            viewModel.onToggle("m-0001@s2")
            latest(this)

            viewModel.onClear()
            val state = latest(this)
            assertFalse(state.hasHistory)
            assertTrue(state.missions.isEmpty())
            assertNull(state.expandedId)
            assertTrue(history.events.value.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
