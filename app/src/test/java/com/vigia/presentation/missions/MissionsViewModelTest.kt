package com.vigia.presentation.missions

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.vigia.domain.model.Clock
import com.vigia.domain.model.CommandStatus
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.Facility
import com.vigia.domain.model.PatrolPace
import com.vigia.domain.model.RejectReason
import com.vigia.domain.model.RobotState
import com.vigia.domain.model.RobotTelemetry
import com.vigia.fakes.FakeRobotRepository
import com.vigia.fakes.MainDispatcherRule
import com.vigia.fakes.sampleTelemetry
import com.vigia.presentation.CommandStatusUi
import com.vigia.presentation.StatusTone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MissionsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val robot = FakeRobotRepository()

    private fun TestScope.viewModel() = MissionsViewModel(robot, Clock { testScheduler.currentTime })

    private fun TestScope.report(telemetry: RobotTelemetry) {
        robot.telemetry.value = telemetry
        robot.lastUpdateMillis.value = testScheduler.currentTime
    }

    private fun docked(battery: Int = 80) = sampleTelemetry(state = RobotState.DOCKED, battery = battery, mission = null)

    // Corre lo pendiente y devuelve el último estado emitido
    private suspend fun TestScope.latest(states: ReceiveTurbine<MissionsUiState>): MissionsUiState {
        runCurrent()
        return states.expectMostRecentItem()
    }

    private fun MissionsUiState.route(id: String) = routes.single { it.id == id }

    @Test
    fun estimatedDurationIsLengthAtPatrolSpeedPlusDwellPerCheckpoint() {
        Facility.routes.forEach { route ->
            val expected = Facility.routeLengthMeters(route) / 0.8 + PatrolPace.checkpointsToVerify(route) * 3.0
            assertEquals(expected, PatrolPace.estimatedSeconds(route), 1e-9)
        }
    }

    @Test
    fun listsTheThreeRoutesWithCountLengthAndDuration() = runTest {
        report(docked())
        viewModel().state.test {
            val state = latest(this)
            assertEquals(listOf("ronda-completa", "ronda-perimetral", "zonas-criticas"), state.routes.map { it.id })

            // 70,43 m / 0,8 m/s + 2 puntos x 3 s = 94,04 s
            val critical = state.route("zonas-criticas")
            assertEquals("Zonas críticas", critical.name)
            assertEquals(2, critical.checkpointCount)
            assertEquals(70, critical.lengthMeters)
            assertEquals(94L, critical.estimatedSeconds)
            assertEquals("1 min 34 s", critical.durationText)
            assertEquals(listOf("Laboratorio de informática", "Sala técnica"), critical.checkpointNames)

            // 77,35 m / 0,8 m/s + 8 puntos x 3 s = 120,69 s
            val full = state.route("ronda-completa")
            assertEquals(8, full.checkpointCount)
            assertEquals(77, full.lengthMeters)
            assertEquals("2 min 1 s", full.durationText)

            assertEquals(3, state.route("ronda-perimetral").checkpointCount)
            assertTrue(state.start.enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun startIsDisabledWithTheReasonFromCommandRules() = runTest {
        viewModel().state.test {
            assertEquals("Sin datos del robot", latest(this).start.reason)

            report(docked(battery = 20))
            val lowBattery = latest(this).start
            assertFalse(lowBattery.enabled)
            assertEquals("Batería por debajo del 30 %", lowBattery.reason)

            report(sampleTelemetry(state = RobotState.PATROLLING))
            assertEquals("El robot tiene que estar en base o listo", latest(this).start.reason)

            report(docked())
            assertTrue(latest(this).start.enabled)
            advanceTimeBy(4_000)
            val stale = latest(this).start
            assertFalse(stale.enabled)
            assertEquals("Sin datos recientes del robot", stale.reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirmedStartSendsTheRouteAndAsksToGoBackToSupervision() = runTest {
        report(docked())
        val viewModel = viewModel()
        viewModel.state.test {
            assertFalse(latest(this).startConfirmed)
            viewModel.onStart("zonas-criticas")

            val state = latest(this)
            val sent = robot.commands.value.single().command
            assertEquals(CommandType.START_MISSION, sent.type)
            assertEquals(mapOf("routeId" to "zonas-criticas"), sent.params)
            assertTrue(state.startConfirmed)
            assertEquals(CommandStatusUi("Confirmada", StatusTone.OK), state.route("zonas-criticas").lastStatus)
            assertNull(state.route("ronda-completa").lastStatus)

            viewModel.onStartHandled()
            assertFalse(latest(this).startConfirmed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rejectedStartStaysOnMissionsWithTheReason() = runTest {
        report(docked())
        robot.nextStatus = CommandStatus.REJECTED
        robot.nextReason = RejectReason.BATTERY_TOO_LOW
        val viewModel = viewModel()
        viewModel.state.test {
            latest(this)
            viewModel.onStart("ronda-completa")

            val state = latest(this)
            assertFalse(state.startConfirmed)
            assertEquals(
                CommandStatusUi("Rechazada: Batería insuficiente", StatusTone.ERROR),
                state.route("ronda-completa").lastStatus,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
