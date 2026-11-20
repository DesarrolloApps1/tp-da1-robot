# Vigía

Vigía es la interfaz HMI/HRI que armamos para el TP de Desarrollo de Aplicaciones I. Es una app Android para supervisar a un Unitree Go2 que hace la ronda nocturna en la sede de la facultad: el guardia de la central ve dónde está el robot, qué puntos verificó y qué alertas tiene abiertas, y le da órdenes simples como iniciar, pausar o volver a la base. No hay un robot real conectado, toda la telemetría es simulada dentro de la app.

## Estructura del repo

- `app/`: la app Android (Kotlin, Jetpack Compose).
- `docs/informe/`: el informe del TP en LaTeX.
- `docs/consigna/`: la consigna original del trabajo.
- **Diseño (Figma):** [prototipo navegable](https://www.figma.com/proto/NjGubKLrsxdJOGcPsqDZXD/Vigia-Prototipo-HMI?node-id=1-7001&starting-point-node-id=1-7001) (flujo normal y flujo con falla) y [archivo de diseño](https://www.figma.com/design/NjGubKLrsxdJOGcPsqDZXD/Vigia-Prototipo-HMI). Cómo se generan los frames: [docs/figma/README.md](docs/figma/README.md).
- `PLAN.md`: el plan de trabajo del equipo.

## Cómo compilar

Abrí la carpeta del repo en Android Studio y dejá que sincronice Gradle. Desde la terminal:

```bash
./gradlew assembleDebug        # genera el APK de debug
./gradlew testDebugUnitTest    # corre los tests unitarios
```

## Cómo probarlo

Compilá e instalá la app desde Android Studio en un emulador o en un teléfono con Android 9 o superior (API 28+). No necesita internet: el robot y su telemetría se simulan dentro de la app.

## Demo de los cinco escenarios

Todo se maneja desde el **panel de simulación**, el ícono de herramienta en la barra superior. Está en todas las pestañas y es solo para la demo: el guardia no lo vería en la app real. Antes de empezar, elegí Vigía-01 en la selección de robot.

1. **Misión normal**
   1. En Supervisión tocá "Elegir ronda" (o andá a Misiones) e iniciá la Ronda completa.
   2. En el panel poné la velocidad en x5 para no esperar.
   3. En Supervisión mirá cómo avanza por los puntos de control y cómo suben los verificados, hasta que vuelve a la base.
2. **Batería baja**
   1. Iniciá una ronda.
   2. En el panel tocá 24 %: aparece la advertencia ámbar con el botón "Volver a la base".
   3. Tocá 14 %: el robot avisa batería crítica, vuelve solo a la base y no deja reanudar.
3. **Desconexión**
   1. Con una ronda en curso, apagá "Conexión con el robot" en el panel.
   2. En Supervisión los datos quedan en gris con "hace N s" y cualquier orden termina en "Sin respuesta" a los 5 s.
   3. Prendé la conexión de nuevo: la telemetría se actualiza. Si la dejás cortada más de 60 s, el robot vuelve solo a la base.
4. **Incidente (obstáculo)**
   1. Con el robot patrullando, tocá "Obstáculo en el pasillo".
   2. El robot pasa a Incidente y en Supervisión aparece "Incidente: requiere tu decisión".
   3. Elegí esperar, saltear el punto o volver a la base. La decisión queda en Alertas y en el Historial.
5. **Escenario propio: persona en zona restringida**
   1. Con el robot patrullando, tocá "Persona en zona restringida".
   2. El diálogo muestra lo que dijo el robot ("Zona restringida. Seguridad fue notificada.") y la nota de privacidad.
   3. Clasificá: intruso, empleado autorizado o falsa alarma. Solo se guarda la zona, la hora y tu clasificación.

Extras del panel: "Rechazar próxima orden" hace que la siguiente orden vuelva rechazada, y "Puerta abierta" suma una alerta de la salida de emergencia sin frenar la ronda.

## Hoja de ruta

El alcance, las decisiones y el orden de los PRs están en [PLAN.md](PLAN.md).
