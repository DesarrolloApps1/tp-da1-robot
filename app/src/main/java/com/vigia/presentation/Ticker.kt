package com.vigia.presentation

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Recalcula la antigüedad de los datos aunque no llegue telemetría nueva
fun ticker(periodMillis: Long): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(periodMillis)
    }
}
