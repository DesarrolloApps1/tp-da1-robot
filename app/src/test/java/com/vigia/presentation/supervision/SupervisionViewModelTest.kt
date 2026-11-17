package com.vigia.presentation.supervision

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.vigia.domain.model.Clock
import com.vigia.domain.model.CommandStatus
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.IncidentDecision
import com.vigia.domain.model.RejectReason
import com.vigia.domain.model.RobotState
import com.vigia.domain.model.RobotTelemetry
import com.vigia.domain.rules.BatteryLevel
import com.vigia.domain.rules.CommandRules
import com.vigia.fakes.FakeRobotRepository
import com.vigia.fakes.MainDispatcherRule
import com.vigia.fakes.sampleIncident
import com.vigia.fakes.sampleTelemetry
import com.vigia.presentation.CommandStatusUi
import com.vigia.presentation.StatusTone
import kotlinx.coroutines.CompletableDeferred
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
class SupervisionViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val robot = FakeRobotRepository()

    private fun TestScope.viewModel() = SupervisionViewModel(robot, Clock { testScheduler.currentTime })

    private fun TestScope.report(telemetry: RobotTelemetry) {
        robot.telemetry.value = telemetry
        robot.lastUpdateMillis.value = testScheduler.currentTime
    }

    // Corre lo pendiente y devuelve el último estado emitido
    private suspend fun TestScope.latest(states: ReceiveTurbine<SupervisionUiState>): SupervisionUiState {
        runCurrent()
        return states.expectMostRecentItem()
    }

    private fun SupervisionUiState.enabled(type: CommandType) = command(type).availability.enabled

    @Test
    fun withoutDataOnlyStopIsEnabled() = runTest {
        viewModel().state.test {
            val state = latest(this)
            assertTrue(state.stale)
            assertEquals("Sin datos", state.connectionLabel)
            assertEquals("Sin datos", state.ageText)
            assertEquals(listOf(CommandType.OPERATIONAL_STOP), CommandType.entries.filter { state.enabled(it) })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun dataGoesStaleAfterThreeSecondsWithoutUpdates() = runTest {
        report(sampleTelemetry())
        viewModel().state.test {
            val fresh = latest(this)
            assertFalse(fresh.stale)
            assertEquals("Conectado", fresh.connectionLabel)
            assertEquals("hace 0 s", fresh.ageText)

            advanceTimeBy(3_000)
            val atLimit = latest(this)
            assertFalse(atLimit.stale)
            assertEquals("hace 3 s", atLimit.ageText)

            advanceTimeBy(1_000)
            val stale = latest(this)
            assertTrue(stale.stale)
            assertEquals("Sin datos", stale.connectionLabel)
            assertEquals(StatusTone.ERROR, stale.connectionTone)
            assertEquals("hace 4 s", stale.ageText)
            assertEquals(StatusTone.STALE, stale.batteryTone)
            assertEquals(StatusTone.STALE, stale.stateTone)
            assertEquals(StatusTone.STALE, stale.dataTone)
            assertTrue(stale.map.stale)
            assertEquals(CommandRules.STALE_WARNING, stale.command(CommandType.PAUSE_MISSION).availability.warning)

            report(sampleTelemetry())
            val back = latest(this)
            assertFalse(back.stale)
            assertEquals("Conectado", back.connectionLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun weakSignalBelowMinus75Dbm() = runTest {
        report(sampleTelemetry(rssi = -76))
        viewModel().state.test {
            val weak = latest(this)
            assertEquals("Señal débil", weak.connectionLabel)
            assertEquals(StatusTone.WARNING, weak.connectionTone)

            report(sampleTelemetry(rssi = -75))
            assertEquals("Conectado", latest(this).connectionLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun availabilityFollowsCommandRules() = runTest {
        report(sampleTelemetry(state = RobotState.PATROLLING))
        viewModel().state.test {
            val patrolling = latest(this)
            assertTrue(patrolling.enabled(CommandType.PAUSE_MISSION))
            assertTrue(patrolling.enabled(CommandType.CANCEL_MISSION))
            assertFalse(patrolling.enabled(CommandType.START_MISSION))
            assertFalse(patrolling.canChooseRoute)
            val resume = patrolling.command(CommandType.RESUME_MISSION).availability
            assertFalse(resume.enabled)
            assertEquals("La misión no está en pausa", resume.reason)

            report(sampleTelemetry(state = RobotState.STOPPED))
            assertTrue(latest(this).enabled(CommandType.CANCEL_MISSION))

            report(sampleTelemetry(state = RobotState.DOCKED, mission = null))
            val docked = latest(this)
            assertTrue(docked.canChooseRoute)
            assertTrue(docked.enabled(CommandType.START_MISSION))
            assertEquals("No hay una misión en curso", docked.command(CommandType.CANCEL_MISSION).availability.reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unresolvedDecisionInIncidentStateIsAnOpenIncident() = runTest {
        report(sampleTelemetry(state = RobotState.INCIDENT))
        robot.alerts.value = listOf(sampleIncident())
        viewModel().state.test {
            val open = latest(this)
            assertEquals("al-0001", open.openIncident?.id)
            assertTrue(open.enabled(CommandType.RESOLVE_INCIDENT))
            assertTrue(open.enabled(CommandType.ACK_ALERT))

            robot.alerts.value = listOf(sampleIncident().copy(acknowledged = true, resolution = IncidentDecision.WAIT))
            val resolved = latest(this)
            assertNull(resolved.openIncident)
            assertFalse(resolved.enabled(CommandType.RESOLVE_INCIDENT))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onCommandSendsAndShowsConfirmation() = runTest {
        report(sampleTelemetry())
        val viewModel = viewModel()
        viewModel.state.test {
            latest(this)
            viewModel.onCommand(CommandType.PAUSE_MISSION)

            val state = latest(this)
            assertEquals(CommandType.PAUSE_MISSION, robot.commands.value.single().command.type)
            assertEquals(CommandStatusUi("Confirmada", StatusTone.OK), state.command(CommandType.PAUSE_MISSION).lastStatus)
            assertNull(state.command(CommandType.CANCEL_MISSION).lastStatus)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onCommandForwardsParams() = runTest {
        report(sampleTelemetry(state = RobotState.DOCKED, mission = null))
        val viewModel = viewModel()
        viewModel.onCommand(CommandType.START_MISSION, mapOf("routeId" to "zonas-criticas"))
        runCurrent()
        assertEquals(mapOf("routeId" to "zonas-criticas"), robot.commands.value.single().command.params)
    }

    @Test
    fun pendingCommandBlocksTheSameTypeUntilItResolves() = runTest {
        report(sampleTelemetry())
        val gate = CompletableDeferred<Unit>()
        robot.ackGate = gate
        val viewModel = viewModel()
        viewModel.state.test {
            latest(this)
            viewModel.onCommand(CommandType.PAUSE_MISSION)

            val pending = latest(this).command(CommandType.PAUSE_MISSION)
            assertEquals(CommandStatusUi("Pendiente", StatusTone.NEUTRAL), pending.lastStatus)
            assertFalse(pending.availability.enabled)
            assertEquals("Hay una orden igual pendiente", pending.availability.reason)

            gate.complete(Unit)
            val done = latest(this).command(CommandType.PAUSE_MISSION)
            assertEquals("Confirmada", done.lastStatus?.label)
            assertTrue(done.availability.enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rejectedAndUnansweredCommandsShowTheirStatus() = runTest {
        report(sampleTelemetry())
        val viewModel = viewModel()
        viewModel.state.test {
            latest(this)
            robot.nextStatus = CommandStatus.REJECTED
            robot.nextReason = RejectReason.PATH_BLOCKED
            viewModel.onCommand(CommandType.RETURN_TO_BASE)
            assertEquals(
                CommandStatusUi("Rechazada: Camino bloqueado", StatusTone.ERROR),
                latest(this).command(CommandType.RETURN_TO_BASE).lastStatus,
            )

            robot.nextStatus = CommandStatus.NO_RESPONSE
            robot.nextReason = null
            viewModel.onCommand(CommandType.OPERATIONAL_STOP)
            assertEquals(
                CommandStatusUi("Sin respuesta", StatusTone.WARNING),
                latest(this).command(CommandType.OPERATIONAL_STOP).lastStatus,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun lowBatteryWarnsOutsideTheBase() = runTest {
        report(sampleTelemetry(battery = 20))
        viewModel().state.test {
            val low = latest(this)
            assertEquals(BatteryLevel.LOW, low.batteryWarning)
            assertEquals(StatusTone.WARNING, low.batteryTone)

            report(sampleTelemetry(battery = 10))
            val critical = latest(this)
            assertEquals(BatteryLevel.CRITICAL, critical.batteryWarning)
            assertEquals(StatusTone.ERROR, critical.batteryTone)

            report(sampleTelemetry(state = RobotState.DOCKED, battery = 10, mission = null))
            assertNull(latest(this).batteryWarning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun mapShowsRouteProgressAndRobot() = runTest {
        report(sampleTelemetry())
        viewModel().state.test {
            val map = latest(this).map
            assertEquals("ronda-completa", map.routeId)
            assertEquals(setOf("CP1", "CP2", "CP3"), map.verifiedIds)
            assertEquals("CP4", map.currentCheckpointId)
            assertEquals("Pasillo A", map.robot?.zone)
            assertFalse(map.stale)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
