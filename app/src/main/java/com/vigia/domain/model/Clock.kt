package com.vigia.domain.model

// Fuente de tiempo inyectable para poder testear sin depender del reloj real
fun interface Clock {
    fun now(): Long
}

object SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}
