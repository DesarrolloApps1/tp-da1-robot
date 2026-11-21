package com.vigia.presentation.simulation

import app.cash.turbine.test
import com.vigia.domain.model.RobotState
import com.vigia.fakes.FakeRobotRepository
import com.vigia.fakes.FakeSimulationControls
import com.vigia.fakes.MainDispatcherRule
import com.vigia.fakes.sampleTelemetry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val controls = FakeSimulationControls()
    private val robot = FakeRobotRepository()

    @Test
    fun stateFollowsControlsAndTelemetry() = runTest {
        val viewModel = SimulationViewModel(controls, robot)
        viewModel.state.test {
            runCurrent()
            val initial = expectMostRecentItem()
            assertTrue(initial.connected)
            assertEquals(1, initial.speedFactor)
            assertNull(initial.batteryPercent)

            robot.telemetry.value = sampleTelemetry(state = RobotState.PATROLLING, battery = 64)
            viewModel.onConnectedChange(false)
            viewModel.onSpeed(5)
            runCurrent()
            val current = expectMostRecentItem()
            assertFalse(current.connected)
            assertEquals(5, current.speedFactor)
            assertEquals(64, current.batteryPercent)
            assertEquals("Patrullando", current.robotStateLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun incidentsOnlyWhilePatrolling() {
        assertFalse(simulationState(true, 1, null).canTriggerIncident)
        assertFalse(simulationState(true, 1, sampleTelemetry(state = RobotState.DOCKED, mission = null)).canTriggerIncident)
        assertFalse(simulationState(true, 1, sampleTelemetry(state = RobotState.INCIDENT)).canTriggerIncident)
        assertTrue(simulationState(true, 1, sampleTelemetry(state = RobotState.PATROLLING)).canTriggerIncident)
    }

    @Test
    fun eventsReachSimulationControls() {
        val viewModel = SimulationViewModel(controls, robot)

        viewModel.onRejectNext()
        viewModel.onObstacle()
        viewModel.onPerson()
        viewModel.onOpenDoor()

        assertEquals(listOf("reject", "obstacle", "person", "door"), controls.calls)
    }

    @Test
    fun batteryIsClampedToValidRange() {
        val viewModel = SimulationViewModel(controls, robot)

        viewModel.onBattery(14)
        assertEquals(14, controls.battery)
        viewModel.onBattery(120)
        assertEquals(100, controls.battery)
        viewModel.onBattery(-5)
        assertEquals(0, controls.battery)
    }
}
