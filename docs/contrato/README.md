# Ejemplos del contrato JSON

Mensajes reales que intercambian `SimulatedRobotRepository` y `RobotSimulator` durante una ronda simulada de las 3 AM.
El contrato es una propuesta nuestra para un gateway propio entre la app y el robot. No es un protocolo oficial de Unitree.

| Archivo | Qué es |
|---|---|
| `telemetria-patrullando.json` | Telemetría con el robot patrullando por el pasillo A, camino al punto CP3 |
| `telemetria-bateria-baja.json` | Telemetría del momento en que la batería baja del 25 % durante la ronda |
| `orden-iniciar-mision.json` | Orden `START_MISSION` para la ronda completa |
| `ack-aceptada.json` | Respuesta `ACCEPTED` a esa orden |
| `ack-rechazada-bateria-baja.json` | Respuesta `REJECTED` con motivo `BATTERY_TOO_LOW` a un arranque con 20 % de batería |
| `orden-resolver-incidente.json` | Orden `RESOLVE_INCIDENT`: el guardia decide esperar ante el obstáculo |
| `alerta-obstaculo.json` | Alerta `OBSTACLE` que pide una decisión del guardia |
| `alerta-persona-zona-restringida.json` | Alerta `PERSON_IN_RESTRICTED_ZONE` en el laboratorio |

Los números con muchos decimales (posición, progreso) son tal cual los manda el simulador.
El simulador gasta batería mucho más rápido que un Go2 real para que las demos sean cortas, por eso `minutesLeft` da tan bajo.

`ContractExamplesTest` corre la misma ronda con un reloj fijo, compara cada archivo con lo que genera el simulador y verifica que se pueda pasar al modelo de dominio.
Si alguien cambia el contrato o el simulador, el test falla hasta que se actualicen estos archivos.
