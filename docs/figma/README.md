# Vigía · Diseño (Figma)

Frames del prototipo navegable que pide la consigna (sección 3). No tenemos acceso directo a Figma, así que todo sale de `build.py`: el script dibuja cada pantalla como un SVG que Figma importa con capas y textos editables. **Los SVG no se editan a mano**: se cambia el script y se regenera.

Los colores son los de `app/src/main/java/com/vigia/ui/theme/Theme.kt` (tema oscuro y `StatusColors`), los textos están copiados de las pantallas de Compose y el plano usa las coordenadas de `domain/model/Facility.kt`, así que el prototipo coincide con la app.

```
docs/figma/
├── build.py      # genera todo: tokens, componentes, pantallas, tableros y galería
├── screens/      # 21 frames Android de 360 × 800 dp para importar en Figma
├── boards/       # mapa de navegación, sistema de diseño, flujo normal y flujo con falla
├── png/          # exportes @2x de frames y tableros (para el informe)
└── index.html    # galería con el estado y la decisión de diseño de cada frame
```

## Regenerar

Hace falta Python 3 (sin dependencias). Para los PNG se usa Microsoft Edge en modo headless, que ya viene con Windows.

```bash
cd docs/figma
python build.py          # screens/, boards/ e index.html
python build.py --png    # además exporta png/ a 2x
```

Para ver todo junto, abrí `index.html` en el navegador.

## Importar en Figma

1. Creá un archivo nuevo con tres páginas: `Pantallas`, `Tableros` y `Prototipo`.
2. En `Pantallas`, arrastrá todos los `.svg` de `screens/` al lienzo. Cada archivo queda como un frame de 360 × 800 con su nombre (`03-supervision-patrullando`, etc.). Seleccionalos todos y usá *Tidy up* para ordenarlos.
3. Las fuentes son **Inter** y **JetBrains Mono**, las dos vienen incluidas en Figma. Si algún texto aparece con otra fuente, seleccioná el frame y elegí *Replace fonts*.
4. En `Tableros`, arrastrá los cuatro `.svg` de `boards/`. Son de referencia: no hace falta conectarlos.
5. Las capas conservan el nombre del componente de la app (`Command slot/Pausar`, `Tab/Alertas`, `Hold to stop`, `Notice`, `Telemetry card/Batería`...), así es fácil encontrar dónde poner cada conexión.

## Armar el prototipo

En la pestaña *Prototype*, seleccioná la capa de la columna "Capa", arrastrá el círculo hasta el frame destino y elegí el disparador. Para los cambios que vienen del robot o de la conexión usá *After delay*, así el prototipo avanza solo como en la app.

| Desde | Capa | Hacia | Disparador |
|---|---|---|---|
| 01 | `Robot/Vigía-01` | 02 | On tap |
| 02 | `Command slot/Elegir ronda` | 07 | On tap |
| 07 | `Route card/Ronda completa` › `Button/filled/Iniciar` | 03 | On tap |
| 03 | `Command slot/Volver a la base` | 04 | On tap |
| 04 | `Text button/Volver a la base` | 06a | On tap |
| 04 | `Text button/Descartar` | 03 | On tap |
| 06a | el frame | 06b | After delay, 1000 ms |
| 03 | `Hold to stop` | 05 | While pressing |
| 09 | `Notice` › `Button/filled/Volver a la base` | 04 | On tap |
| 10 | `Command slot/Pausar` | 06d | On tap |
| 06d | el frame | 11 | After delay, 3000 ms |
| 12 | `Notice` › `Button/filled/Decidir` | 13 | On tap |
| 13 | `Text button/Enviar decisión` | 03 | On tap |
| 13 | `Text button/Más tarde` | 12 | On tap |
| 15 | `Alert card/Persona en zona restringida` › `Button/filled/Decidir` | 14 | On tap |
| 16 | `Mission card/m-0003` | 17 | On tap |
| 17 | `Mission card/m-0003` | 16 | On tap |
| Todos los que tienen barra | `Tab/Supervisión`, `Tab/Misiones`, `Tab/Alertas`, `Tab/Historial` | 03, 07, 15, 16 | On tap |
| Todos los que tienen barra | `Abrir panel de simulación` | 18 | On tap |
| 18 | `Control/Conexión con el robot` | 10 | On tap |
| 18 | `Control/Obstáculo en el pasillo` | 12 | On tap |
| 18 | `Control/Persona en zona restringida` | 14 | On tap |
| 18 | `Battery control` › `Button/outlined/24 %` | 09 | On tap |

Las fallas se disparan desde el panel de simulación (18), igual que en la demo de la app. Así no hace falta más de un *After delay* por frame, que es lo que permite Figma.

Para la presentación, creá dos *flow starting points*:

- **Flujo normal**, desde 01: 01 → 02 → 07 → 03 → 16 → 17 (ver `boards/00-flujo-normal.svg`).
- **Flujo con falla**, desde 03: abrí el panel y cortá la conexión (10 → 06d → 11), o dispará el obstáculo (12 → 13 → 03). Ver `boards/00-flujo-con-falla.svg`.

El mapa completo con todas las flechas está en `boards/00-mapa-de-navegacion.svg`.

## Frames

| Frame | Pantalla | Estado |
|---|---|---|
| 01 | Selección de robot | Normal y deshabilitado (Vigía-02 fuera de servicio) |
| 02 | Supervisión, robot en base | Normal; Cancelar y Volver a la base deshabilitados con su motivo |
| 03 | Supervisión, patrullando | Normal: plano con la ruta, tarjetas y barra de órdenes |
| 04 | Confirmar "Volver a la base" | Confirmación |
| 05 | Detención mantenida | Anillo de progreso mientras se mantiene STOP |
| 06a | Orden pendiente | Pendiente; el botón se deshabilita |
| 06b | Orden confirmada | Confirmada; el robot pasa a Regresando |
| 06c | Orden rechazada | Error: "Rechazada: Camino bloqueado" |
| 06d | Orden sin respuesta | Advertencia: sin acuse a los 5 s |
| 07 | Misiones | Normal: Iniciar habilitado |
| 08 | Misiones con batería baja | Deshabilitado: "Batería por debajo del 30 %" |
| 09 | Batería baja en Supervisión | Advertencia con acceso a Volver a la base |
| 10 | Desconexión | Error y datos antiguos: banner, todo en gris, "Sin datos hace 12 s" |
| 11 | Reconexión | Normal, con el chip Sin respuesta de la orden anterior |
| 12 | Obstáculo en el pasillo B | Error: estado Incidente y banner con Decidir |
| 13 | Decisión ante el obstáculo | Diálogo con una opción elegida |
| 14 | Persona en zona restringida | Diálogo con lo que dijo el robot y la nota de privacidad; Enviar deshabilitado |
| 15 | Alertas | Activas y reconocidas, con el color de la severidad |
| 16 | Historial | Rondas guardadas y filtro |
| 17 | Detalle de ronda | Línea de tiempo de la ronda con el obstáculo |
| 18 | Panel de simulación | Hoja inferior, solo para la demo |

Los tableros completan lo que pide la consigna:

- `00-mapa-de-navegacion`: todos los frames y cómo se llega a cada uno.
- `00-sistema-de-diseno`: colores, tipografía, plano y los componentes reutilizables (botón de orden, chip de estado, tarjeta de telemetría, detención mantenida, aviso y banner, tarjeta de robot, diálogo de decisión) en estado normal, deshabilitado, advertencia y error.
- `00-flujo-normal` y `00-flujo-con-falla`: los recorridos de los escenarios 1, 3 y 4.

## Diferencias con la app

- La app usa Roboto (la fuente del sistema); en Figma usamos Inter, que tiene medidas parecidas, y JetBrains Mono para los números.
