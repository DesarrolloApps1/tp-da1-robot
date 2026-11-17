package com.vigia.presentation.alerts

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.vigia.domain.model.AlertKind
import com.vigia.domain.model.AlertSeverity
import com.vigia.domain.model.CommandStatus
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.IncidentDecision
import com.vigia.domain.model.RobotState
import com.vigia.fakes.FakeRobotRepository
import com.vigia.fakes.MainDispatcherRule
import com.vigia.fakes.sampleAlert
import com.vigia.fakes.sampleTelemetry
import com.vigia.presentation.CommandStatusUi
import com.vigia.presentation.PRIVACY_NOTE
import com.vigia.presentation.ROBOT_RESTRICTED_ZONE_LINE
import com.vigia.presentation.StatusTone
import kotlinx.coroutines.CompletableDeferred
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
class AlertsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val robot = FakeRobotRepository()

    private val person = sampleAlert(
        "al-0004",
        AlertKind.PERSON_IN_RESTRICTED_ZONE,
        raisedAtMillis = 4_000,
        severity = AlertSeverity.CRITICAL,
        requiresDecision = true,
    )

    // Corre lo pendiente y devuelve el último estado emitido
    private suspend fun TestScope.latest(states: ReceiveTurbine<AlertsUiState>): AlertsUiState {
        runCurrent()
        return states.expectMostRecentItem()
    }

    private fun AlertsUiState.item(id: String) = (active + past).single { it.alert.id == id }

    @Test
    fun activeAlertsComeFirstAndNewestFirst() = runTest {
        robot.telemetry.value = sampleTelemetry()
        robot.alerts.value = listOf(
            sampleAlert("al-0002", AlertKind.DOOR_OPEN, raisedAtMillis = 2_000),
            sampleAlert("al-0003", AlertKind.LOW_BATTERY, raisedAtMillis = 3_000, acknowledged = true),
            sampleAlert(
                "al-0001",
                AlertKind.OBSTACLE,
                raisedAtMillis = 1_000,
                requiresDecision = true,
                acknowledged = true,
                resolution = IncidentDecision.WAIT,
            ),
            person,
        )
        AlertsViewModel(robot).state.test {
            val state = latest(this)
            assertEquals(listOf("al-0004", "al-0002"), state.active.map { it.alert.id })
            assertEquals(listOf("al-0003", "al-0001"), state.past.map { it.alert.id })

            val critical = state.item("al-0004")
            assertEquals("Persona en zona restringida", critical.kindLabel)
            assertEquals("Crítica", critical.severityLabel)
            assertEquals(StatusTone.ERROR, critical.severityTone)
            assertTrue(critical.canAcknowledge)
            assertNull(critical.resolutionLabel)
            assertEquals("Reconocida", state.item("al-0003").resolutionLabel)
            assertEquals("Decisión: Esperar", state.item("al-0001").resolutionLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun acknowledgeSendsTheAlertIdAndWaitsForTheRobot() = runTest {
        robot.telemetry.value = sampleTelemetry()
        val door = sampleAlert("al-0002", AlertKind.DOOR_OPEN, raisedAtMillis = 2_000)
        robot.alerts.value = listOf(door)
        val gate = CompletableDeferred<Unit>()
        robot.ackGate = gate
        val viewModel = AlertsViewModel(robot)
        viewModel.state.test {
            latest(this)
            viewModel.onAcknowledge("al-0002")

            val pending = latest(this).item("al-0002")
            val sent = robot.commands.value.single().command
            assertEquals(CommandType.ACK_ALERT, sent.type)
            assertEquals(mapOf("alertId" to "al-0002"), sent.params)
            assertEquals(CommandStatusUi("Pendiente", StatusTone.NEUTRAL), pending.ackStatus)
            assertFalse(pending.canAcknowledge)

            gate.complete(Unit)
            assertEquals("Confirmada", latest(this).item("al-0002").ackStatus?.label)

            // El repositorio marca la alerta cuando el robot confirma
            robot.alerts.value = listOf(door.copy(acknowledged = true))
            val done = latest(this)
            assertTrue(done.active.isEmpty())
            assertEquals("Reconocida", done.item("al-0002").resolutionLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun decisionIsSentAndConfirmedForTheOpenIncident() = runTest {
        robot.telemetry.value = sampleTelemetry(state = RobotState.INCIDENT)
        robot.alerts.value = listOf(person)
        val gate = CompletableDeferred<Unit>()
        robot.ackGate = gate
        val viewModel = AlertsViewModel(robot)
        viewModel.state.test {
            val open = latest(this).item("al-0004")
            assertTrue(open.awaitingDecision)
            val decision = open.decision!!
            assertTrue(decision.canDecide)
            assertEquals(
                listOf(IncidentDecision.INTRUDER, IncidentDecision.AUTHORIZED_EMPLOYEE, IncidentDecision.FALSE_ALARM),
                decision.options.map { it.decision },
            )
            assertTrue(decision.options.all { it.explanation.isNotBlank() })
            assertEquals(ROBOT_RESTRICTED_ZONE_LINE, decision.robotSaid)
            assertEquals(PRIVACY_NOTE, decision.privacyNote)

            viewModel.onDecide("al-0004", IncidentDecision.INTRUDER)
            val pending = latest(this).item("al-0004")
            val sent = robot.commands.value.single().command
            assertEquals(CommandType.RESOLVE_INCIDENT, sent.type)
            assertEquals(mapOf("alertId" to "al-0004", "decision" to "INTRUDER"), sent.params)
            assertTrue(pending.awaitingDecision)
            assertTrue(pending.decision!!.pending)
            assertFalse(pending.decision!!.canDecide)
            assertEquals(IncidentDecision.INTRUDER, pending.decision!!.sentDecision)

            gate.complete(Unit)
            robot.telemetry.value = sampleTelemetry(state = RobotState.PAUSED)
            robot.alerts.value = listOf(person.copy(acknowledged = true, resolution = IncidentDecision.INTRUDER))
            val state = latest(this)
            assertTrue(state.active.isEmpty())
            assertEquals("Decisión: Intruso", state.item("al-0004").resolutionLabel)
            // El diálogo sigue teniendo datos para mostrar la confirmación
            assertEquals(CommandStatusUi("Confirmada", StatusTone.OK), state.decision("al-0004")?.lastStatus)
            assertEquals(IncidentDecision.INTRUDER, state.decision("al-0004")?.resolution)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unansweredDecisionLetsTheGuardTryAgain() = runTest {
        robot.telemetry.value = sampleTelemetry(state = RobotState.INCIDENT)
        robot.alerts.value = listOf(person)
        robot.nextStatus = CommandStatus.NO_RESPONSE
        val viewModel = AlertsViewModel(robot)
        viewModel.state.test {
            latest(this)
            viewModel.onDecide("al-0004", IncidentDecision.FALSE_ALARM)

            val item = latest(this).item("al-0004")
            val decision = item.decision!!
            assertEquals(CommandStatusUi("Sin respuesta", StatusTone.WARNING), decision.lastStatus)
            assertNull(decision.resolution)
            assertTrue(decision.canDecide)
            assertTrue(item.awaitingDecision)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun decisionOnlyWhileTheRobotWaitsInIncident() = runTest {
        val obstacle = sampleAlert("al-0001", AlertKind.OBSTACLE, raisedAtMillis = 1_000, requiresDecision = true)
        robot.telemetry.value = sampleTelemetry(state = RobotState.RETURNING)
        robot.alerts.value = listOf(obstacle)
        AlertsViewModel(robot).state.test {
            val item = latest(this).item("al-0001")
            val decision = item.decision!!
            assertFalse(item.awaitingDecision)
            assertFalse(decision.canDecide)
            assertNull(decision.robotSaid)
            assertNull(decision.privacyNote)
            assertEquals(
                listOf(IncidentDecision.WAIT, IncidentDecision.SKIP_CHECKPOINT, IncidentDecision.RETURN_TO_BASE),
                decision.options.map { it.decision },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
