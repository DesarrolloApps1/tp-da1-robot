package com.vigia.domain.usecase

import com.vigia.domain.model.AlertSeverity
import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.EventType
import com.vigia.domain.model.MissionProgress
import com.vigia.domain.model.MissionRecord
import com.vigia.domain.model.MissionResult
import com.vigia.domain.model.Pose
import com.vigia.domain.model.RobotState
import com.vigia.domain.model.RobotTelemetry
import com.vigia.domain.model.ServiceCounters
import com.vigia.fakes.FakeHistoryRepository
import com.vigia.fakes.FakeRobotRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryRecorderTest {

    private val robot = FakeRobotRepository()

    private fun TestScope.startRecorder(history: FakeHistoryRepository) {
        HistoryRecorder(robot, history, sessionId = "s1").start(backgroundScope)
        runCurrent()
    }

    private fun TestScope.report(
        state: RobotState,
        at: Long,
        verified: Int = 0,
        people: Int = 0,
        doors: Int = 0,
        missionId: String? = "m-0001",
    ) {
        robot.telemetry.value = RobotTelemetry(
            robotId = "go2-vigia-01",
            seq = at,
            timestampMillis = at,
            rssi = -60,
            latencyMs = 80,
            batteryPercent = 80,
            minutesLeft = 90,
            charging = false,
            state = state,
            mission = missionId?.let { MissionProgress(it, "ronda-completa", 0.5, "CP3", verified, 8) },
            pose = Pose(10.0, 5.0, 0.0, "Hall"),
            speed = 0.8,
            moving = true,
            service = ServiceCounters(verified, people, doors),
        )
        runCurrent()
    }

    @Test
    fun newMissionIsSavedInProgress() = runTest {
        val history = FakeHistoryRepository()
        startRecorder(history)

        report(RobotState.PATROLLING, at = 1_000)

        val record = history.upserts.single()
        assertEquals("m-0001@s1", record.id)
        assertEquals("Ronda completa", record.routeName)
        assertEquals(1_000L, record.startedAtMillis)
        assertNull(record.endedAtMillis)
        assertEquals(MissionResult.IN_PROGRESS, record.result)
        assertEquals(8, record.checkpointsTotal)
    }

    @Test
    fun onlyWritesWhenCountersChange() = runTest {
        val history = FakeHistoryRepository()
        startRecorder(history)

        report(RobotState.PATROLLING, at = 1_000)
        report(RobotState.PATROLLING, at = 2_000)
        report(RobotState.PATROLLING, at = 3_000, verified = 1)

        assertEquals(2, history.upserts.size)
        assertEquals(1, history.upserts.last().checkpointsVerified)
        assertEquals(1_000L, history.upserts.last().startedAtMillis)
    }

    @Test
    fun completedMissionKeepsFinalCounters() = runTest {
        val history = FakeHistoryRepository()
        startRecorder(history)

        report(RobotState.PATROLLING, at = 1_000)
        report(RobotState.DOCKED, at = 90_000, verified = 8, people = 1, doors = 2, missionId = null)

        val record = history.missions.value.single()
        assertEquals(MissionResult.COMPLETED, record.result)
        assertEquals(90_000L, record.endedAtMillis)
        assertEquals(8, record.checkpointsVerified)
        assertEquals(1, record.peopleDetected)
        assertEquals(2, record.openDoors)
    }

    @Test
    fun returnToBaseAndCancelAreToldApart() = runTest {
        val history = FakeHistoryRepository()
        startRecorder(history)

        report(RobotState.PATROLLING, at = 1_000)
        report(RobotState.RETURNING, at = 2_000, verified = 2)
        report(RobotState.DOCKED, at = 3_000, verified = 2, missionId = null)
        report(RobotState.PATROLLING, at = 4_000, missionId = "m-0002")
        report(RobotState.IDLE, at = 5_000, missionId = null)

        val results = history.missions.value.associate { it.id to it.result }
        assertEquals(MissionResult.RETURNED_TO_BASE, results["m-0001@s1"])
        assertEquals(MissionResult.CANCELLED, results["m-0002@s1"])
    }

    @Test
    fun staleMissionsFromEarlierRunsAreAborted() = runTest {
        val stale = MissionRecord(
            id = "m-0001@s0",
            routeId = "zonas-criticas",
            routeName = "Zonas críticas",
            startedAtMillis = 1_000,
            endedAtMillis = null,
            result = MissionResult.IN_PROGRESS,
            checkpointsVerified = 1,
            checkpointsTotal = 2,
            peopleDetected = 0,
            openDoors = 0,
        )
        val history = FakeHistoryRepository(listOf(stale))
        startRecorder(history)

        report(RobotState.PATROLLING, at = 5_000)

        val results = history.missions.value.associate { it.id to it.result }
        assertEquals(MissionResult.ABORTED, results["m-0001@s0"])
        assertEquals(MissionResult.IN_PROGRESS, results["m-0001@s1"])
    }

    @Test
    fun eventsAreForwardedWithSessionMissionId() = runTest {
        val history = FakeHistoryRepository()
        HistoryRecorder(robot, history, sessionId = "s1").start(backgroundScope)

        // Sin runCurrent antes de emitir: el recorder ya tiene que estar suscripto
        robot.events.tryEmit(event(EventType.MISSION, "Misión m-0001 iniciada: Ronda completa", "m-0001"))
        robot.events.tryEmit(event(EventType.CONNECTION, "Se perdió la conexión con el robot", null))
        runCurrent()

        val saved = history.events.value.reversed()
        assertEquals(listOf("m-0001@s1", null), saved.map { it.missionId })
        assertEquals(listOf(EventType.MISSION, EventType.CONNECTION), saved.map { it.type })
    }

    private fun event(type: EventType, message: String, missionId: String?) = EventRecord(
        timestampMillis = 1_000,
        missionId = missionId,
        type = type,
        severity = AlertSeverity.INFO,
        message = message,
        zone = null,
    )
}
