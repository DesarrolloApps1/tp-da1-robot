# Evidencias

Capturas de la app corriendo en un emulador Pixel 8 (Android 16). Se probó la versión con los arreglos de #16, #17 y #18 (PR #28).

| Archivo | Qué muestra |
|---|---|
| `01-seleccion-robot.png` | Selección de robot: Vigía-01 disponible y Vigía-02 deshabilitado |
| `02-supervision.png` | Supervisión con el robot en la base |
| `03-misiones.png` | Las tres rondas con distancia y duración estimada |
| `04-panel.png` | Panel de simulación |
| `e1-01-ronda-en-curso.png` | Escenario 1: ronda completa en curso |
| `e1-02-ronda-contadores.png` | Escenario 1: puntos verificados, personas y puertas |
| `e1-03-ronda-terminada.png` | Escenario 1: el robot vuelve a la base |
| `e1-04-historial.png` | Escenario 1: la ronda queda como completada 8/8 |
| `e2-01-bateria-baja.png` | Escenario 2: aviso de batería baja con "Volver a la base" |
| `e2-02-bateria-critica.png` | Escenario 2: batería crítica, el robot regresa solo y no se puede reanudar |
| `e2-03-alertas-bateria.png` | Escenario 2: alertas de batería baja y crítica |
| `e3-01-sin-conexion.png` | Escenario 3: datos en gris y aviso "Sin datos hace N s" |
| `e3-02-orden-pendiente.png` | Escenario 3: orden enviada sin conexión |
| `e3-03-orden-sin-respuesta.png` | Escenario 3: pasados 5 s la orden queda "Sin respuesta" |
| `e3-04-reconexion.png` | Escenario 3: más de 60 s sin enlace, al reconectar el robot ya está regresando solo |
| `e3-05-historial.png` | Escenario 3: la ronda queda como "Vuelta a la base" |
| `e3-06-historial-fin-sin-conexion.png` | #16: la ronda termina sin enlace y aun así queda como "Vuelta a la base" |
| `e4-01-obstaculo-pasillo-b.png` | Escenario 4: obstáculo en el pasillo B, el robot pasa a incidente |
| `e4-02-decision.png` | Escenario 4: el guardia elige esperar, saltear el punto o volver a la base |
| `e4-03-plano-punto-salteado.png` | #17: se salteó CP2 y el plano marca CP1 y CP3, no CP2 |
| `e4-04-decision-confirmada.png` | Escenario 4: decisión "Esperar" confirmada por el robot |
| `e5-01-persona-zona-restringida.png` | Escenario 5: persona en el laboratorio, incidente que pide decisión |
| `e5-02-decision.png` | Escenario 5: intruso, empleado autorizado o falsa alarma |
| `e5-03-decision-confirmada.png` | Escenario 5: decisión "Falsa alarma" confirmada |
| `e5-04-alertas.png` | Escenario 5: la alerta queda en Alertas con la decisión |
| `e5-05-historial-todas.png` | Historial con todas las rondas |
| `e5-06-historial-con-incidentes.png` | #18: "Con incidentes" no muestra la ronda que solo tuvo batería baja |

## Videos

| Archivo | Qué muestra |
|---|---|
| `video/flujo-normal.mp4` | Flujo normal en x5: iniciar la ronda completa, recorrido y registro en el historial |
| `video/flujo-con-falla.mp4` | Flujo con falla en x5: obstáculo y decisión del guardia, corte de conexión con orden sin respuesta y reconexión |

Los videos se grabaron con `adb screenrecord` y no tienen audio.
