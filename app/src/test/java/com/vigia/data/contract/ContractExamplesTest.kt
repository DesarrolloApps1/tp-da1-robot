package com.vigia.data.contract

import com.vigia.data.sim.RobotSimulator
import com.vigia.domain.model.AlertKind
import com.vigia.domain.model.AlertSeverity
import com.vigia.domain.model.Clock
import com.vigia.domain.model.Command
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.IncidentDecision
import com.vigia.domain.model.RejectReason
import com.vigia.domain.model.RobotState
import com.vigia.domain.rules.BatteryLevel
import com.vigia.domain.rules.BatteryRules
import java.io.File
import java.time.Instant
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los ejemplos de docs/contrato salen de esta ronda simulada con reloj fijo.
 * Si cambia el contrato o el simulador, este test falla hasta que se regeneren.
 */
class ContractExamplesTest {

    private val night = NightRound().apply { run() }

    @Test
    fun everyExampleFileComesFromTheSimulator() {
        val files = contractDir().list { _, name -> name.endsWith(".json") }.orEmpty().toSet()

        assertEquals(night.examples.keys, files)
    }

    @Test
    fun examplesMatchSimulatorOutput() {
        night.examples.forEach { (file, example) ->
            assertEquals(file, example.generated(), example.decode(read(file)))
        }
    }

    @Test
    fun patrollingTelemetryMapsToDomain() {
        val telemetry = decode("telemetria-patrullando.json", TelemetryDto.serializer()).toDomain()

        assertEquals(RobotState.PATROLLING, telemetry.state)
        assertTrue(telemetry.moving)
        assertEquals(BatteryLevel.OK, BatteryRules.level(telemetry.batteryPercent))
        assertEquals("ronda-completa", telemetry.mission?.routeId)
    }

    @Test
    fun lowBatteryTelemetryMapsToDomain() {
        val telemetry = decode("telemetria-bateria-baja.json", TelemetryDto.serializer()).toDomain()

        assertEquals(RobotState.PATROLLING, telemetry.state)
        assertEquals(BatteryLevel.LOW, BatteryRules.level(telemetry.batteryPercent))
        assertNotNull(telemetry.mission)
    }

    @Test
    fun startCommandAndAcksMapToDomain() {
        val command = decode("orden-iniciar-mision.json", CommandDto.serializer()).toDomain()
        val accepted = decode("ack-aceptada.json", CommandAckDto.serializer())
        val rejected = decode("ack-rechazada-bateria-baja.json", CommandAckDto.serializer())

        assertEquals(CommandType.START_MISSION, command.type)
        assertEquals("ronda-completa", command.params["routeId"])
        assertEquals(command.id, accepted.commandId)
        assertEquals(CommandAckDto.ACCEPTED, accepted.result)
        assertNull(accepted.reason)
        assertEquals(CommandAckDto.REJECTED, rejected.result)
        assertEquals(RejectReason.BATTERY_TOO_LOW, RejectReason.valueOf(requireNotNull(rejected.reason)))
    }

    @Test
    fun resolveCommandAnswersTheObstacleAlert() {
        val alert = decode("alerta-obstaculo.json", AlertDto.serializer()).toDomain()
        val command = decode("orden-resolver-incidente.json", CommandDto.serializer()).toDomain()

        assertEquals(AlertKind.OBSTACLE, alert.kind)
        assertEquals(AlertSeverity.CRITICAL, alert.severity)
        assertTrue(alert.requiresDecision)
        assertEquals(CommandType.RESOLVE_INCIDENT, command.type)
        assertEquals(alert.id, command.params["alertId"])
        assertEquals(alert.kind, IncidentDecision.valueOf(requireNotNull(command.params["decision"])).appliesTo)
    }

    @Test
    fun personAlertMapsToDomain() {
        val alert = decode("alerta-persona-zona-restringida.json", AlertDto.serializer()).toDomain()

        assertEquals(AlertKind.PERSON_IN_RESTRICTED_ZONE, alert.kind)
        assertTrue(alert.requiresDecision)
        assertFalse(alert.acknowledged)
    }

    private fun <T> decode(file: String, serializer: KSerializer<T>): T =
        ContractJson.decodeFromString(serializer, read(file))

    private fun read(file: String): String = File(contractDir(), file).readText()

    // Gradle corre los tests desde app/, así que se busca docs/contrato hacia arriba
    private fun contractDir(): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "docs/contrato") }
            .first { it.isDirectory }

    private class Example<T>(val serializer: KSerializer<T>, val json: String) {
        fun generated(): T = decode(json)
        fun decode(text: String): T = ContractJson.decodeFromString(serializer, text)
    }

    /** Ronda de las 3 AM: arranque rechazado, ronda, obstáculo, persona en el laboratorio y batería baja. */
    private class NightRound {
        private var now = Instant.parse("2026-11-16T03:00:00Z").toEpochMilli()
        private val sim = RobotSimulator(Clock { now })
        private var commandCount = 0
        private var telemetry = ""
        private val alerts = mutableListOf<String>()

        val examples = linkedMapOf<String, Example<*>>()

        fun run() {
            // El panel de simulación deja la batería en 20 % y el robot rechaza el arranque
            sim.setBattery(20)
            tick()
            send(CommandType.START_MISSION, mapOf("routeId" to "ronda-completa")).also { (_, ack) ->
                examples["ack-rechazada-bateria-baja.json"] = Example(CommandAckDto.serializer(), ack)
            }

            // Carga en la base y arranca la ronda
            repeat(40) { tick() }
            send(CommandType.START_MISSION, mapOf("routeId" to "ronda-completa")).also { (command, ack) ->
                examples["orden-iniciar-mision.json"] = Example(CommandDto.serializer(), command)
                examples["ack-aceptada.json"] = Example(CommandAckDto.serializer(), ack)
            }

            repeat(25) { tick() }
            examples["telemetria-patrullando.json"] = Example(TelemetryDto.serializer(), telemetry)

            // Una silla bloquea el pasillo y el guardia decide esperar
            repeat(2) { tick() }
            sim.obstacleAhead()
            tick()
            val obstacle = alerts.removeAt(alerts.lastIndex)
            examples["alerta-obstaculo.json"] = Example(AlertDto.serializer(), obstacle)
            repeat(5) { tick() }
            val obstacleId = ContractJson.decodeFromString(AlertDto.serializer(), obstacle).alertId
            send(CommandType.RESOLVE_INCIDENT, mapOf("alertId" to obstacleId, "decision" to "WAIT")).also { (command, _) ->
                examples["orden-resolver-incidente.json"] = Example(CommandDto.serializer(), command)
            }

            // Persona en el laboratorio: el guardia la clasifica como falsa alarma
            tickUntil { decodeTelemetry().pose.zone == "Laboratorio" }
            sim.personInRestrictedZone()
            tick()
            val person = alerts.removeAt(alerts.lastIndex)
            examples["alerta-persona-zona-restringida.json"] = Example(AlertDto.serializer(), person)
            repeat(4) { tick() }
            val personId = ContractJson.decodeFromString(AlertDto.serializer(), person).alertId
            send(CommandType.RESOLVE_INCIDENT, mapOf("alertId" to personId, "decision" to "FALSE_ALARM"))

            // La ronda sigue hasta que la batería baja del 25 %
            tickUntil { alerts.isNotEmpty() }
            examples["telemetria-bateria-baja.json"] = Example(TelemetryDto.serializer(), telemetry)
        }

        // Lo mismo que hace SimulatedRobotRepository en cada segundo
        private fun tick() {
            now += 1_000
            sim.advance(1.0)
            telemetry = sim.telemetryJson()
            alerts += sim.drainAlerts()
            sim.drainEvents()
        }

        private fun tickUntil(condition: () -> Boolean) {
            repeat(600) {
                if (condition()) return
                tick()
            }
            error("La ronda no llegó al momento esperado")
        }

        private fun decodeTelemetry() = ContractJson.decodeFromString(TelemetryDto.serializer(), telemetry).toDomain()

        // La orden sale ya y el ack llega 600 ms después, como en el repositorio
        private fun send(type: CommandType, params: Map<String, String>): Pair<String, String> {
            val id = "cmd-" + (++commandCount).toString().padStart(4, '0')
            val command = Command(id, type, params, now).toDto(RobotSimulator.ROBOT_ID)
            val json = ContractJson.encodeToString(CommandDto.serializer(), command)
            now += 600
            return json to sim.receive(json)
        }
    }
}
