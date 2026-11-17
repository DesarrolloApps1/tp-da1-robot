package com.vigia.fakes

import com.vigia.domain.repository.SimulationControls
import kotlinx.coroutines.flow.MutableStateFlow

// Registra cada control que toca el panel para verificarlo desde el test
class FakeSimulationControls : SimulationControls {
    override val speedFactor = MutableStateFlow(1)
    override val connected = MutableStateFlow(true)

    val calls = mutableListOf<String>()
    var battery: Int? = null

    override fun setConnected(connected: Boolean) {
        this.connected.value = connected
    }

    override fun rejectNextCommand() {
        calls += "reject"
    }

    override fun setBattery(percent: Int) {
        battery = percent
    }

    override fun triggerObstacle() {
        calls += "obstacle"
    }

    override fun triggerPersonInRestrictedZone() {
        calls += "person"
    }

    override fun triggerOpenDoor() {
        calls += "door"
    }

    override fun setSpeedFactor(factor: Int) {
        speedFactor.value = factor
    }
}
