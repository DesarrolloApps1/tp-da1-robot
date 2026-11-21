package com.vigia.presentation.simulation

data class DemoScenario(val title: String, val instruction: String)

// Los cinco escenarios de PLAN.md con lo que tiene que hacer quien presenta
val DemoScenarios = listOf(
    DemoScenario(
        "1. Misión normal",
        "En Misiones iniciá la Ronda completa, poné x5 y esperá a que vuelva a la base.",
    ),
    DemoScenario(
        "2. Batería baja",
        "Con la ronda en curso tocá 24 % y usá Volver a la base, o tocá 14 %: el robot vuelve solo y no deja reanudar.",
    ),
    DemoScenario(
        "3. Desconexión",
        "Apagá la conexión: los datos quedan en gris y las órdenes sin respuesta. Prendela de nuevo y todo se actualiza.",
    ),
    DemoScenario(
        "4. Incidente",
        "Durante la ronda tocá Obstáculo en el pasillo y decidí desde Supervisión: esperar, saltear o volver a la base.",
    ),
    DemoScenario(
        "5. Persona en zona restringida",
        "Durante la ronda tocá Persona en zona restringida y clasificala: intruso, empleado autorizado o falsa alarma.",
    ),
)
