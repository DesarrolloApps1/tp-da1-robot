package com.vigia.presentation.main

import app.cash.turbine.test
import com.vigia.domain.model.AlertKind
import com.vigia.fakes.FakeRobotRepository
import com.vigia.fakes.MainDispatcherRule
import com.vigia.fakes.sampleAlert
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val robot = FakeRobotRepository()

    @Test
    fun badgeCountsUnacknowledgedAlerts() = runTest {
        robot.alerts.value = listOf(
            sampleAlert("al-0001", AlertKind.DOOR_OPEN, raisedAtMillis = 1_000),
            sampleAlert("al-0002", AlertKind.LOW_BATTERY, raisedAtMillis = 2_000, acknowledged = true),
        )
        MainViewModel(robot).state.test {
            runCurrent()
            assertEquals(1, expectMostRecentItem().unacknowledgedAlerts)

            robot.alerts.value = robot.alerts.value.map { it.copy(acknowledged = true) }
            runCurrent()
            assertEquals(0, expectMostRecentItem().unacknowledgedAlerts)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
