package com.vigia.fakes

import com.vigia.domain.model.Alert
import com.vigia.domain.model.Command
import com.vigia.domain.model.CommandRecord
import com.vigia.domain.model.CommandStatus
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.RobotSummary
import com.vigia.domain.model.RobotTelemetry
import com.vigia.domain.repository.RobotRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

// Robot controlado a mano desde el test: confirma todas las órdenes al instante
class FakeRobotRepository : RobotRepository {
    override val robots = MutableStateFlow<List<RobotSummary>>(emptyList())
    override val telemetry = MutableStateFlow<RobotTelemetry?>(null)
    override val lastUpdateMillis = MutableStateFlow<Long?>(null)
    override val alerts = MutableStateFlow<List<Alert>>(emptyList())
    override val commands = MutableStateFlow<List<CommandRecord>>(emptyList())
    override val events = MutableSharedFlow<EventRecord>(extraBufferCapacity = 64)

    override fun select(robotId: String) = Unit

    override suspend fun send(type: CommandType, params: Map<String, String>): CommandRecord {
        val command = Command("cmd-${commands.value.size + 1}", type, params, issuedAtMillis = 0)
        val record = CommandRecord(command, CommandStatus.CONFIRMED, null, respondedAtMillis = 0)
        commands.value = listOf(record) + commands.value
        return record
    }
}
