package com.vigia.presentation.robotselect

import app.cash.turbine.test
import com.vigia.domain.model.RobotSummary
import com.vigia.fakes.FakeRobotRepository
import com.vigia.fakes.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RobotSelectViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val robots = listOf(
        RobotSummary("go2-vigia-01", "Vigía-01", "Unitree Go2", available = true, note = "Simulado"),
        RobotSummary("go2-vigia-02", "Vigía-02", "Unitree Go2", available = false, note = "Fuera de servicio"),
    )

    @Test
    fun listsRobotsAndForwardsTheSelection() = runTest {
        val repository = FakeRobotRepository().apply { this.robots.value = this@RobotSelectViewModelTest.robots }
        val viewModel = RobotSelectViewModel(repository)

        viewModel.state.test {
            assertEquals(robots, awaitItem().robots)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.onSelect("go2-vigia-01")
        assertEquals("go2-vigia-01", repository.selectedRobotId)
    }
}
