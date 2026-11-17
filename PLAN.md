# Plan de trabajo · Vigía

TP grupal de Desarrollo de Aplicaciones I: interfaz HMI/HRI para un Unitree Go2 con telemetría simulada. La consigna está en [docs/consigna/TP-HMI-Ottoman.pdf](docs/consigna/TP-HMI-Ottoman.pdf).

Este archivo es la referencia del equipo. Si algo cambia (alcance, nombres, decisiones), se actualiza acá en el mismo PR.

## 1. Caso elegido

**Go2 haciendo la ronda nocturna en la sede de la facultad.**

Lo elegimos sobre la inspección de la sala de servidores por tres motivos:

- Hay personas en escena (guardia, personal de limpieza, alguien que se quedó adentro), así que la parte de HRI tiene contenido real y no queda forzada.
- El escenario propio sale solo: alguien en una zona restringida a las 3 de la mañana, ¿es un intruso, un empleado o una falsa alarma?
- Es un uso que ya existe (empresas de seguridad usan cuadrúpedos para rondas) y el rol del operador es justo lo que pide el TP: un guardia en la central que supervisa y decide.

### Problema

De noche la sede queda con un solo guardia. Hoy la ronda se hace de dos formas: el guardia camina el piso y deja la central sin nadie, o se queda mirando cámaras fijas que tienen puntos ciegos (pasillos laterales, depósito, salida de emergencia). En ninguno de los dos casos queda registro confiable de qué se verificó y a qué hora.

### Qué hace el robot

El Go2 recorre una ruta fija de puntos de control en planta baja. En cada punto se detiene unos segundos y lo marca como verificado. Si detecta una puerta abierta, un obstáculo o una persona en una zona restringida, avisa a la central y espera la decisión del guardia. El guardia no maneja el robot con joystick: le da órdenes discretas (iniciar, pausar, volver a la base, etc.).

### Por qué Go2 y no G1 ni una alternativa sin robot

- **Go2 frente a G1:** la ronda pide autonomía, estabilidad y pasar por umbrales y escalones bajos, no manipular objetos. El Go2 es más chico, más estable y más barato. El G1 aporta brazos que acá no se usan.
- **Frente a más cámaras fijas:** las cámaras no se mueven hasta el punto ciego ni pueden verificar una puerta de cerca. Además no dejan un registro de "punto verificado".
- **Frente a un segundo guardia:** el costo mensual es alto y el problema es justamente que de noche hay una sola persona.

### Qué existe y qué haría falta (para el documento)

| Disponible en Go2 (según la versión) | Requeriría equipamiento o desarrollo extra |
|---|---|
| Locomoción en interiores, escalones bajos | Detección confiable de puertas abiertas (modelo de visión propio) |
| LiDAR 4D y cámara frontal | Clasificación de personas en zona restringida |
| Batería intercambiable, 1 a 2 h de autonomía aprox. | Base de carga automática (dock de terceros) |
| SDK (versión EDU) para órdenes de alto nivel | Gateway propio entre app y robot (WiFi del edificio) |
| Parlante y luces en algunas versiones | Mensajes de voz para HRI configurables |

Las cifras exactas se completan en el documento con fuentes de Unitree (ver issue de investigación técnica).

## 2. Entorno y recorrido

Planta baja de la sede, de 22:00 a 06:00. Plano simplificado de 40 × 20 m.

| Id | Punto de control | Tipo |
|---|---|---|
| BASE | Base de carga (junto a la central de seguridad) | Punto seguro |
| CP1 | Hall de entrada y puerta principal | Puerta |
| CP2 | Molinetes de acceso | Paso |
| CP3 | Pasillo de aulas A | Paso |
| CP4 | Laboratorio de informática | Zona restringida |
| CP5 | Biblioteca | Paso |
| CP6 | Sala técnica | Zona restringida |
| CP7 | Salida de emergencia lateral | Puerta |
| CP8 | Pasillo de aulas B | Paso |

Rutas predefinidas:

- **Ronda completa:** BASE → CP1 → … → CP8 → BASE.
- **Ronda perimetral:** BASE → CP1 → CP7 → CP2 → BASE (solo puertas y accesos).
- **Zonas críticas:** BASE → CP4 → CP6 → BASE.

## 3. Roles

| Rol | Qué necesita | Cuándo interviene |
|---|---|---|
| Guardia de la central (operador) | Ver dónde está el robot, qué verificó, alertas abiertas | Inicia rondas, decide ante incidentes, reconoce alertas |
| Jefe de seguridad | Historial de rondas y eventos | Revisa el registro al día siguiente |
| Personas en el edificio (limpieza, docentes, alguien que se quedó) | Entender qué es el robot y qué hacer | Se cruzan con el robot; el robot les habla y les da paso |

## 4. Telemetría

Datos comunes (los pide la consigna):

- Conectividad: conectado / señal débil / sin datos, con latencia.
- Batería: porcentaje y minutos estimados.
- Estado operativo: `DOCKED`, `IDLE`, `PATROLLING`, `PAUSED`, `INCIDENT`, `RETURNING`, `STOPPED`, `FAULT`.
- Misión y progreso: ruta, punto actual, porcentaje.
- Ubicación: posición en el plano y zona actual.
- Movimiento: velocidad y rumbo.
- Alertas activas.
- Última actualización: hora y antigüedad. Más de 3 s sin datos marca todo como antiguo.

Variables del servicio (las tres propias):

1. **Puntos de control verificados** (3/8).
2. **Personas detectadas en zona restringida** durante la ronda.
3. **Puertas abiertas detectadas** durante la ronda.

## 5. Comandos

Cada orden pasa por los estados **pendiente → confirmada / rechazada / sin respuesta**. Sin respuesta = 5 s sin ack. "Confirmada" quiere decir que el robot aceptó la orden, no que la terminó: eso se ve después en la telemetría.

| Orden | Se habilita cuando | Rechazo simulado típico |
|---|---|---|
| Iniciar misión | Conectado, estado `DOCKED` o `IDLE`, batería ≥ 30 %, sin incidente abierto | `BATTERY_TOO_LOW`, `ROBOT_BUSY` |
| Pausar | `PATROLLING` | `INVALID_STATE` |
| Reanudar | `PAUSED`, batería ≥ 15 %, sin incidente abierto | `BATTERY_TOO_LOW` |
| Cancelar misión | `PATROLLING`, `PAUSED` o `INCIDENT`. Pide confirmación | `INVALID_STATE` |
| Regresar a punto seguro | Cualquier estado fuera de `DOCKED` y `RETURNING` | `PATH_BLOCKED` |
| Detención operativa | Siempre visible. Se mantiene apretado 1,5 s para evitar toques accidentales | Ninguno; puede quedar sin respuesta |
| Reconocer alerta | Alerta activa sin reconocer | `UNKNOWN_ALERT` |

Mientras hay una orden pendiente no se puede mandar otra del mismo tipo. Sin conexión, los botones siguen visibles pero muestran que la orden puede no llegar.

La detención de la app es un comando de software simulado. No reemplaza la parada de emergencia física del robot. Esto se muestra en la interfaz junto al botón.

## 6. Escenarios de prueba

1. **Misión normal:** iniciar ronda completa, ver progreso por punto de control, volver a la base.
2. **Batería baja:** al bajar del 25 % aparece una advertencia con el botón "Volver a la base". Por debajo del 15 % el robot pide volver por su cuenta y no deja reanudar.
3. **Desconexión:** desde el panel de simulación se corta la conexión. Los datos quedan en gris con "hace 12 s", las órdenes quedan sin respuesta y al reconectar todo se actualiza. Si la desconexión pasa de 60 s, el robot vuelve solo a la base (comportamiento documentado del lado robot).
4. **Incidente:** una silla bloquea el pasillo B. El robot pasa a `INCIDENT` y el guardia elige: esperar, saltear el punto o volver a la base.
5. **Escenario propio:** persona en el laboratorio a las 3 AM. El robot se detiene a distancia, dice por el parlante "Zona restringida. Seguridad fue notificada" y el guardia clasifica: intruso (procedimiento de seguridad), empleado autorizado (se registra) o falsa alarma.

## 7. Riesgos y medidas

| Riesgo | Medida |
|---|---|
| El robot se acerca demasiado a una persona | Velocidad limitada a 0,8 m/s, se detiene a 2 m al detectar a alguien y le da paso |
| Orden accidental (cancelar o detener) | Confirmación para cancelar y mantener apretado para detener; botones separados y con colores distintos |
| Pérdida de conexión | Datos marcados como antiguos, órdenes sin confirmar visibles, regreso autónomo pasados 60 s |
| Privacidad de las personas detectadas | Sin reconocimiento facial y sin guardar imágenes: el historial guarda solo zona, hora y clasificación |
| Fatiga del guardia por falsas alarmas | Alertas con severidad, agrupadas y con reconocimiento; la clasificación "falsa alarma" queda registrada para ajustar la ruta |

## 8. Arquitectura del APK

Kotlin, Jetpack Compose, MVVM con ViewModel y StateFlow, Room para el historial, kotlinx.serialization para el contrato JSON. Inyección manual con un `AppContainer`. Sin internet ni servidor.

```
com.vigia
 ├─ domain/
 │   ├─ model/        RobotTelemetry, RobotState, Mission, Route, Checkpoint, Alert, Command, CommandRecord...
 │   ├─ rules/        CommandRules (habilitación), BatteryRules, Freshness
 │   └─ repository/   RobotRepository, HistoryRepository (interfaces)
 ├─ data/
 │   ├─ contract/     DTOs JSON + mappers (contrato propuesto)
 │   ├─ sim/          RobotSimulator, SimulatedRobotRepository, SimulationControls
 │   └─ local/        Room: base, entidades, DAOs, RoomHistoryRepository
 ├─ presentation/     ViewModels y UiState por pantalla
 ├─ ui/               pantallas, componentes, tema, navegación
 └─ di/               AppContainer
```

Reglas:

- La UI observa `StateFlow` y manda eventos al ViewModel. Nunca toca el simulador.
- El `RobotRepository` es la única puerta a telemetría, órdenes y eventos. La implementación simulada intercambia JSON con el simulador (serializa la orden y parsea la telemetría), así el contrato se usa de verdad y no solo en el documento.
- El simulador corre en una corrutina con un tick de 1 s y un multiplicador de velocidad (x1, x5) para las demos.
- Toda la lógica de habilitación y umbrales vive en `domain/rules` y tiene tests unitarios.

### Contrato JSON propuesto

No es el protocolo oficial de Unitree. Es lo que proponemos que hable un gateway propio entre la app y el robot.

Telemetría:

```json
{
  "type": "telemetry",
  "robotId": "go2-vigia-01",
  "seq": 1532,
  "timestamp": "2026-11-16T03:14:05Z",
  "connection": { "rssi": -62, "latencyMs": 85 },
  "battery": { "percent": 64, "minutesLeft": 72, "charging": false },
  "state": "PATROLLING",
  "mission": { "id": "m-0007", "routeId": "ronda-completa", "progress": 0.42, "currentCheckpoint": "CP4", "checkpointsVerified": 3, "checkpointsTotal": 8 },
  "pose": { "x": 18.4, "y": 6.2, "heading": 90, "zone": "Laboratorio de informática" },
  "motion": { "speed": 0.8, "moving": true },
  "service": { "checkpointsVerified": 3, "peopleInRestrictedZone": 0, "openDoors": 1 }
}
```

Orden y respuesta:

```json
{ "type": "command", "commandId": "cmd-0042", "robotId": "go2-vigia-01", "command": "START_MISSION", "params": { "routeId": "ronda-completa" }, "issuedAt": "2026-11-16T03:10:00Z" }
{ "type": "command_ack", "commandId": "cmd-0042", "result": "ACCEPTED", "reason": null, "respondedAt": "2026-11-16T03:10:01Z" }
{ "type": "command_ack", "commandId": "cmd-0043", "result": "REJECTED", "reason": "BATTERY_TOO_LOW", "respondedAt": "2026-11-16T03:10:02Z" }
```

Alerta:

```json
{ "type": "alert", "alertId": "al-77", "severity": "CRITICAL", "kind": "PERSON_IN_RESTRICTED_ZONE", "zone": "Laboratorio de informática", "message": "Persona detectada en zona restringida", "raisedAt": "2026-11-16T03:14:02Z", "requiresDecision": true }
```

## 9. Pantallas

| Pantalla | Contenido |
|---|---|
| Selección de robot | Lista de robots (Vigía-01 simulado, Vigía-02 fuera de servicio para mostrar el estado deshabilitado) |
| Supervisión | Plano con ruta y posición, tarjetas de telemetría, progreso, barra de órdenes, banner de desconexión |
| Misiones | Rutas disponibles con duración estimada; iniciar desde acá |
| Alertas | Alertas activas y reconocidas; diálogo de decisión para incidentes y personas |
| Historial | Rondas y eventos guardados en Room |
| Panel de simulación | Hoja inferior para forzar desconexión, rechazo, batería baja, obstáculo, persona, puerta abierta y cambiar la velocidad |

Tema oscuro por defecto: el guardia trabaja de noche en una central con poca luz. Ámbar para advertencias, rojo para errores, verde para estado normal, gris para datos antiguos.

## 10. Hoja de ruta (un PR por feature)

Branches `joa/<feature>` desde `dev`, PR a `dev`. Los PRs del código están apilados: cada branch sale de la anterior, así que **se mergean en orden**. Después de mergear uno, el siguiente muestra solo sus commits.

| # | Branch | Contenido | Estado |
|---|---|---|---|
| 1 | `joa/project-plan` | Este plan, README y consigna en `docs/` | Hecho |
| 2 | `joa/android-scaffold` | Proyecto Gradle, tema, navegación vacía, CI | Pendiente |
| 3 | `joa/domain-model` | Modelos y reglas de dominio con tests | Pendiente |
| 4 | `joa/robot-simulator` | Contrato JSON, simulador y repositorio simulado con tests | Pendiente |
| 5 | `joa/history-room` | Historial en Room con tests | Pendiente |
| 6 | `joa/supervision-screen` | Selección de robot, supervisión y barra de órdenes | Pendiente |
| 7 | `joa/alerts-incidents` | Alertas, reconocimiento y decisiones de incidentes | Pendiente |
| 8 | `joa/missions-history-screens` | Pantallas de misiones e historial | Pendiente |
| 9 | `joa/simulation-panel` | Panel de simulación con los cinco escenarios | Pendiente |
| 10 | `joa/latex-docs` | Documento en LaTeX en `docs/informe` | Pendiente |

Lo que no se termine queda como issue en el repo, con el número de fila de esta tabla.

### Fuera de este plan (issues aparte)

- Prototipo en Figma: mapa de navegación, componentes, estados (normal, deshabilitado, advertencia, error), flujo normal y flujo con falla.
- Investigación técnica del Go2 con fuentes (sensores, autonomía, versiones AIR/PRO/EDU).
- Evidencias de los cinco escenarios (capturas) y video de demostración.
- Diagrama de arquitectura exportado para la entrega.

## 11. Convenciones

- Código, archivos y nombres en inglés. Textos de la UI, comentarios y documentación en español.
- Commits cortos en imperativo, uno por cambio lógico.
- `./gradlew assembleDebug testDebugUnitTest` tiene que pasar antes de abrir un PR (lo corre el CI).
- La lógica nueva va con tests en `app/src/test`.
