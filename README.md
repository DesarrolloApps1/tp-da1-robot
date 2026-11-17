# Vigía

Vigía es la interfaz HMI/HRI que armamos para el TP de Desarrollo de Aplicaciones I. Es una app Android para supervisar a un Unitree Go2 que hace la ronda nocturna en la sede de la facultad: el guardia de la central ve dónde está el robot, qué puntos verificó y qué alertas tiene abiertas, y le da órdenes simples como iniciar, pausar o volver a la base. No hay un robot real conectado, toda la telemetría es simulada dentro de la app.

## Estructura del repo

- `app/`: la app Android (Kotlin, Jetpack Compose).
- `docs/informe/`: el informe del TP en LaTeX.
- `docs/consigna/`: la consigna original del trabajo.
- `PLAN.md`: el plan de trabajo del equipo.

## Cómo compilar

Abrí la carpeta del repo en Android Studio y dejá que sincronice Gradle. Desde la terminal:

```bash
./gradlew assembleDebug        # genera el APK de debug
./gradlew testDebugUnitTest    # corre los tests unitarios
```

## Hoja de ruta

El alcance, las decisiones y el orden de los PRs están en [PLAN.md](PLAN.md).
