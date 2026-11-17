#!/usr/bin/env python3
"""
Vigía: generador de frames para Figma.

Cada pantalla se genera como un SVG de 360x800 (dp de Android) con capas vectoriales
y texto editable. Arrastrar los .svg de `screens/` y `boards/` a Figma crea un frame
por archivo. Los colores salen de app/src/main/java/com/vigia/ui/theme/Theme.kt y el
plano de domain/model/Facility.kt, así que el prototipo coincide con la app.

Uso:
    python build.py            # genera screens/, boards/ e index.html
    python build.py --png      # además exporta png/ @2x con Edge headless
"""
import html
import math
import os
import shutil
import subprocess
import sys
import tempfile
import time

ROOT = os.path.dirname(os.path.abspath(__file__))
W, H = 360, 800

# ---------------------------------------------------------------- tokens ----
# Palette y StatusColors de ui/theme/Theme.kt (tema oscuro por defecto)
C = dict(
    navy="#0B1220",       # background
    surface="#131C2E",    # surface: barras, hojas
    raised="#1C2740",     # surfaceVariant: tarjetas, plano, diálogos
    line="#2E3A55",       # outline
    ink="#E4E9F2",        # onSurface
    muted="#9AA5BA",      # onSurfaceVariant
    blue="#7FB2FF",       # primary
    blueDeep="#0E3A73",   # primaryContainer
    green="#4CC38A",      # StatusColors.ok
    amber="#F2B53A",      # StatusColors.warning
    red="#F2685C",        # StatusColors.error
    grey="#7A8396",       # StatusColors.stale
    white="#FFFFFF",
)
# StatusTone -> color, igual que StatusTone.color() en StatusChip.kt
TONE = dict(neutral=C["ink"], ok=C["green"], warning=C["amber"], error=C["red"], stale=C["grey"])

SANS = "Inter, sans-serif"
MONO = "JetBrains Mono, monospace"
FONTS = ("https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700"
         "&family=JetBrains+Mono:wght@500;700&display=swap")

# Escala tipográfica de Material 3 (tamaño, interlineado, peso)
T = dict(
    headlineSmall=(24, 32, 400), titleLarge=(22, 28, 400), titleMedium=(16, 24, 500),
    titleSmall=(14, 20, 500), bodyLarge=(16, 24, 400), bodyMedium=(14, 20, 400),
    bodySmall=(12, 16, 400), labelLarge=(14, 20, 500), labelMedium=(12, 16, 500), map=(8, 10, 400),
)


# ------------------------------------------------------------ primitivas ----
def esc(s):
    return html.escape(str(s), quote=True)


def num(v):
    return f"{v:.1f}".rstrip("0").rstrip(".") if isinstance(v, float) else str(v)


def attrs(**kw):
    out = []
    for k, v in kw.items():
        if v is None:
            continue
        out.append(f'{k.replace("_", "-")}="{num(v) if isinstance(v, (int, float)) else v}"')
    return " ".join(out)


def rect(x, y, w, h, r=0, fill="none", stroke=None, sw=1, opacity=None, dash=None, fill_opacity=None):
    return (f'<rect {attrs(x=x, y=y, width=w, height=h, rx=r, fill=fill, fill_opacity=fill_opacity)}'
            + (f' {attrs(stroke=stroke, stroke_width=sw)}' if stroke else "")
            + (f' stroke-dasharray="{dash}"' if dash else "")
            + (f' opacity="{opacity}"' if opacity is not None else "") + "/>")


def circle(cx, cy, r, fill="none", stroke=None, sw=1, opacity=None, fill_opacity=None):
    return (f'<circle {attrs(cx=cx, cy=cy, r=r, fill=fill, fill_opacity=fill_opacity)}'
            + (f' {attrs(stroke=stroke, stroke_width=sw)}' if stroke else "")
            + (f' opacity="{opacity}"' if opacity is not None else "") + "/>")


def line(x1, y1, x2, y2, stroke=C["line"], sw=1, dash=None, opacity=None):
    return (f'<line {attrs(x1=x1, y1=y1, x2=x2, y2=y2, stroke=stroke, stroke_width=sw)} stroke-linecap="round"'
            + (f' stroke-dasharray="{dash}"' if dash else "")
            + (f' opacity="{opacity}"' if opacity is not None else "") + "/>")


def path(d, fill="none", stroke=None, sw=1, opacity=None, dash=None):
    return (f'<path d="{d}" fill="{fill}"'
            + (f' stroke="{stroke}" stroke-width="{num(sw)}" stroke-linecap="round" stroke-linejoin="round"' if stroke else "")
            + (f' stroke-dasharray="{dash}"' if dash else "")
            + (f' opacity="{opacity}"' if opacity is not None else "") + "/>")


def text(x, y, s, size=14, weight=400, fill=C["ink"], family=SANS, anchor="start", lh=None,
         italic=False, opacity=None, ls=None):
    """Texto con baseline en y; cada \\n es un tspan (sigue siendo una sola capa editable)."""
    lines = str(s).split("\n")
    lh = lh or round(size * 1.35)
    a = attrs(x=x, y=y, font_family=family, font_size=size, font_weight=weight, fill=fill, text_anchor=anchor)
    if italic:
        a += ' font-style="italic"'
    if ls is not None:
        a += f' letter-spacing="{ls}"'
    if opacity is not None:
        a += f' opacity="{opacity}"'
    if len(lines) == 1:
        return f"<text {a}>{esc(s)}</text>"
    spans = "".join(f'<tspan x="{num(x)}" dy="{0 if i == 0 else lh}">{esc(ln)}</tspan>' for i, ln in enumerate(lines))
    return f"<text {a}>{spans}</text>"


def g(content, name=None, transform=None, opacity=None, clip=None):
    a = ""
    if name:
        a += f' id="{esc(name)}"'
    if transform:
        a += f' transform="{transform}"'
    if opacity is not None:
        a += f' opacity="{opacity}"'
    if clip:
        a += f' clip-path="url(#{clip})"'
    return f"<g{a}>{''.join(content) if isinstance(content, list) else content}</g>"


def clip_rect(cid, x, y, w, h, r=0):
    return f'<clipPath id="{cid}">{rect(x, y, w, h, r, "#000")}</clipPath>'


def arc(cx, cy, r, frac):
    frac = max(0.001, min(frac, 0.9999))
    a0 = -math.pi / 2
    a1 = a0 + 2 * math.pi * frac
    x0, y0 = cx + r * math.cos(a0), cy + r * math.sin(a0)
    x1, y1 = cx + r * math.cos(a1), cy + r * math.sin(a1)
    return f"M{x0:.1f} {y0:.1f} A{r} {r} 0 {1 if frac > 0.5 else 0} 1 {x1:.1f} {y1:.1f}"


# Ancho aproximado de Inter por carácter (en em); se usa para cortar líneas y medir chips
NARROW = set("iljtfr.,:;'!|() ")
WIDE = set("mwMWOQGD%@→")


def tw(s, size, weight=400):
    em = sum(0.31 if ch in NARROW else 0.88 if ch in WIDE else 0.64 if ch.isupper() or ch.isdigit() else 0.57
             for ch in str(s))
    return em * size * (1.04 if weight >= 600 else 1.0)


def wrap(s, size, maxw, weight=400):
    """Corta el texto en líneas que entran en maxw (respeta los \\n explícitos)."""
    out = []
    for para in str(s).split("\n"):
        cur = ""
        for word in para.split(" "):
            cand = f"{cur} {word}" if cur else word
            if cur and tw(cand, size, weight) > maxw:
                out.append(cur)
                cur = word
            else:
                cur = cand
        out.append(cur)
    return out


def para(x, top, s, style="bodyMedium", fill=C["ink"], maxw=328, anchor="start", family=SANS,
         italic=False, weight=None):
    """Bloque de texto con estilo M3 que empieza en `top`. Devuelve (svg, alto)."""
    size, lh, wgt = T[style]
    wgt = weight or wgt
    lines = wrap(s, size, maxw, wgt)
    base = top + lh / 2 + size * 0.36
    return text(x, base, "\n".join(lines), size, wgt, fill, family, anchor, lh, italic), lh * len(lines)


# ---------------------------------------------------------------- íconos ----
# Material Icons (relleno, grilla de 24): los mismos que usa la app
IC = {
    "home": "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z",
    "list": "M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z",
    "warning": "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z",
    "date": ("M9 11H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2zm2-7h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20"
             "c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V9h14v11z"),
    "build": ("M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7C.4 7.1.9 10.1 2.9 12.1"
              "c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z"),
    "check": "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z",
}


def icon(name, x, y, size=24, color=C["ink"], opacity=None):
    s = size / 24
    return (f'<g transform="translate({num(x)} {num(y)}) scale({s:.4f})"'
            + (f' opacity="{opacity}"' if opacity is not None else "")
            + f'><path d="{IC[name]}" fill="{color}"/></g>')


# ------------------------------------------------------------ componentes ----
def status_bar(clock="03:12"):
    c = C["ink"]
    out = [text(16, 17, clock, 13, 500, c)]
    for i, hgt in enumerate([4, 6, 8, 10]):
        out.append(rect(298 + i * 4, 15 - hgt, 2.5, hgt, 1, c))
    out.append(rect(318, 6, 20, 10, 3, "none", c, 1.2))
    out.append(rect(320, 8, 13, 6, 1.5, c))
    out.append(rect(338.5, 9, 1.5, 4, 0.5, c))
    return g(out, "Status bar")


def gesture_bar():
    return rect(140, 790, 80, 4, 2, C["ink"], opacity=0.5)


def top_app_bar():
    """TopAppBar de MainScreen: título y acceso al panel de simulación."""
    return g([rect(0, 24, W, 64, 0, C["surface"]),
              text(16, 64, "Vigía", 22, 400, C["ink"]),
              g([rect(304, 32, 48, 48, 24), icon("build", 316, 44, 22, C["muted"])], "Abrir panel de simulación")],
             "Top app bar")


TABS = [("home", "Supervisión"), ("list", "Misiones"), ("warning", "Alertas"), ("date", "Historial")]


def nav_bar(active, badge=0):
    """NavigationBar con las cuatro pestañas; Alertas lleva badge con las no reconocidas."""
    out = [rect(0, 720, W, 80, 0, C["surface"])]
    for i, (ic, label) in enumerate(TABS):
        cx = 45 + i * 90
        on = i == active
        tab = [rect(cx - 45, 720, 90, 68)]  # área de toque de la pestaña completa
        if on:
            tab.append(rect(cx - 32, 730, 64, 32, 16, C["blueDeep"]))
        tab.append(icon(ic, cx - 12, 734, 24, C["ink"] if on else C["muted"]))
        if ic == "warning" and badge:
            tab += [circle(cx + 12, 735, 8, C["red"]),
                    text(cx + 12, 739, str(badge), 11, 600, C["navy"], SANS, "middle")]
        tab.append(text(cx, 778, label, 12, 600 if on else 500, C["ink"] if on else C["muted"], SANS, "middle"))
        out.append(g(tab, "Tab/" + label))
    out.append(gesture_bar())
    return g(out, "Navigation bar")


def status_chip(x, y, label, tone, anchor="start", maxw=None):
    """StatusChip: píldora con borde, fondo al 14 % y el texto siempre junto al color.
    Con maxw el texto se corta en líneas, como pasa en la app dentro de un slot angosto."""
    col = TONE[tone]
    lines = wrap(label, 12, maxw - 20, 500) if maxw else [label]
    w = max(tw(ln, 12, 500) for ln in lines) + 20
    h = 6 + 16 * len(lines)
    if anchor == "end":
        x -= w
    elif anchor == "middle":
        x -= w / 2
    bg = rect(x, y, w, h, min(11, h / 2), col, fill_opacity=0.14)
    border = rect(x + 0.5, y + 0.5, w - 1, h - 1, min(10.5, h / 2), "none", col, 1, opacity=0.6)
    tx = x + w / 2 if len(lines) > 1 else x + 10
    t = text(tx, y + 15, "\n".join(lines), 12, 500, col, SANS, "middle" if len(lines) > 1 else "start", 16)
    return g([bg, border, t], "Status chip/" + label), w


def button(x, y, w, label, kind="filled", h=40, color=None, size=14, lines=None):
    """Botones M3. kind: filled, tonal, outlined, danger, text, disabled, disabled-outlined."""
    col = color or C["blue"]
    styles = {
        "filled": (col, None, C["navy"]),
        "tonal": (C["blueDeep"], None, C["ink"]),
        "outlined": ("none", C["line"], C["blue"]),
        "danger": ("none", C["red"], C["red"]),
        "text": ("none", None, col),
        "disabled": (C["ink"], None, C["ink"]),
        "disabled-outlined": ("none", C["line"], C["ink"]),
        "disabled-text": ("none", None, C["ink"]),
    }
    fill, stroke, fg = styles[kind]
    dis = kind.startswith("disabled")
    out = []
    if kind == "disabled":
        out.append(rect(x, y, w, h, 20, C["ink"], opacity=0.12))
    else:
        out.append(rect(x, y, w, h, 20, fill, stroke, 1))
    lines = lines or [label]
    lh = 20
    top = y + (h - lh * len(lines)) / 2
    out.append(text(x + w / 2, top + lh / 2 + size * 0.36, "\n".join(lines), size, 500, fg, SANS, "middle", lh,
                    opacity=0.38 if dis else None))
    return g(out, f"Button/{kind}/{label}")


def button_w(label):
    return tw(label, 14, 500) + 48


def notice(x, y, w, msg, color, bg_color=None, bg_opacity=0.12, actions=None):
    """Notice: aviso con ícono y borde del color de la gravedad. actions: [(svg_fn(x,y)->(svg,h))]."""
    pad = 12
    body, th = para(x + pad + 32, y + pad + 2, msg, "bodyMedium", C["ink"], w - pad * 2 - 32)
    ih = max(24, th + 4)
    content = [icon("warning", x + pad, y + pad + (ih - 24) / 2, 24, color), body]
    cy = y + pad + ih
    for fn in actions or []:
        svg, h = fn(x + pad, cy + 8, w - pad * 2)
        content.append(svg)
        cy += 8 + h
    h = cy + pad - y
    bg = rect(x, y, w, h, 12, bg_color or color, fill_opacity=bg_opacity)
    border = rect(x + 0.5, y + 0.5, w - 1, h - 1, 11.5, "none", color, 1)
    return g([bg, border] + content, "Notice"), h


def telemetry_card_h(w, title, value, detail=None, progress=None, mono=False, tone=None):
    inner = w - 24
    h = 12 + 16 * len(wrap(title, 12, inner, 500)) + 4
    h += 28 * len(wrap(value, 22 if not mono else 20, inner))
    if progress is not None:
        h += 4 + 4
    if detail:
        h += 4 + 16 * len(wrap(detail, 12, inner))
    return h + 12


def telemetry_card(x, y, w, h, title, value, detail=None, tone="neutral", progress=None, mono=False):
    """TelemetryCard: título, valor grande con el color del estado y detalle opcional."""
    col = TONE[tone]
    inner = w - 24
    out = [rect(x, y, w, h, 12, C["raised"])]
    cy = y + 12
    t, th = para(x + 12, cy, title, "labelMedium", C["muted"], inner)
    out.append(t)
    cy += th + 4
    if mono:
        lines = wrap(value, 20, inner, 500)
        out.append(text(x + 12, cy + 14 + 20 * 0.36, "\n".join(lines), 20, 500, col, MONO, lh=28))
        cy += 28 * len(lines)
    else:
        v, vh = para(x + 12, cy, value, "titleLarge", col, inner)
        out.append(v)
        cy += vh
    if progress is not None:
        cy += 4
        out += [rect(x + 12, cy, inner, 4, 2, col, opacity=0.24), rect(x + 12, cy, max(4, inner * progress), 4, 2, col)]
        cy += 4
    if detail:
        cy += 4
        d, _ = para(x + 12, cy, detail, "bodySmall", C["muted"], inner)
        out.append(d)
    return g(out, f"Telemetry card/{title}")


def telemetry_grid(x, y, w, cards):
    """Dos columnas con alto parejo por fila (IntrinsicSize.Min). Devuelve (svg, alto)."""
    cw = (w - 8) / 2
    out, cy = [], y
    for i in range(0, len(cards), 2):
        row = cards[i:i + 2]
        rh = max(telemetry_card_h(cw, **c) for c in row)
        for j, c in enumerate(row):
            out.append(telemetry_card(x + j * (cw + 8), cy, cw, rh, **c))
        cy += rh + 8
    return g(out, "Telemetry grid"), cy - 8 - y


def card(x, y, w, h, stroke=None, opacity=None):
    return rect(x, y, w, h, 12, C["raised"], stroke, 1, opacity)


def radio(cx, cy, selected, enabled=True):
    col = C["blue"] if selected else C["muted"]
    op = None if enabled else 0.38
    out = [circle(cx, cy, 9, "none", col if enabled else C["ink"], 2, opacity=op)]
    if selected:
        out.append(circle(cx, cy, 5, col if enabled else C["ink"], opacity=op))
    return g(out, "Radio")


def switch(x, y, on):
    if on:
        return g([rect(x, y, 52, 32, 16, C["blue"]), circle(x + 36, y + 16, 12, C["navy"]),
                  icon("check", x + 29, y + 9, 14, C["blue"])], "Switch/on")
    return g([rect(x + 1, y + 1, 50, 30, 15, C["raised"], C["muted"], 2), circle(x + 16, y + 16, 8, C["muted"])],
             "Switch/off")


def scrim(opacity=0.5):
    return rect(0, 0, W, H, 0, "#000000", opacity=opacity)


# ----------------------------------------------------------------- plano ----
# Copia de domain/model/Facility.kt (metros, origen arriba a la izquierda)
FACILITY_W, FACILITY_H = 40.0, 20.0
CHECKPOINTS = {
    "BASE": ("Base de carga", 3.0, 3.0),
    "CP1": ("Hall de entrada", 8.0, 2.0),
    "CP2": ("Molinetes", 12.0, 5.0),
    "CP3": ("Pasillo de aulas A", 20.0, 5.0),
    "CP4": ("Laboratorio de informática", 30.0, 4.0),
    "CP5": ("Biblioteca", 34.0, 12.0),
    "CP6": ("Sala técnica", 30.0, 17.0),
    "CP7": ("Salida de emergencia", 20.0, 18.0),
    "CP8": ("Pasillo de aulas B", 10.0, 14.0),
}
ZONES = [  # nombre, left, top, right, bottom, restringida
    ("Central", 0, 0, 6, 6, False), ("Hall", 6, 0, 14, 8, False), ("Pasillo A", 14, 3, 26, 7, False),
    ("Aulas", 14, 7, 26, 12, False), ("Laboratorio", 26, 0, 36, 8, True), ("Biblioteca", 30, 8, 40, 16, False),
    ("Sala técnica", 26, 14, 34, 20, True), ("Pasillo B", 6, 12, 26, 16, False),
    ("Salida de emergencia", 16, 16, 24, 20, False),
]
ROUTES = {
    "ronda-completa": ("Ronda completa", ["BASE", "CP1", "CP2", "CP3", "CP4", "CP5", "CP6", "CP7", "CP8", "BASE"]),
    "ronda-perimetral": ("Ronda perimetral", ["BASE", "CP1", "CP7", "CP2", "BASE"]),
    "zonas-criticas": ("Zonas críticas", ["BASE", "CP4", "CP6", "BASE"]),
}
SPEED_MPS, DWELL_SECONDS = 0.8, 3.0  # PatrolPace.kt
# El rótulo de CP7 va debajo del punto para no pisar el nombre de "Salida de emergencia" (igual que FacilityMap.kt)
LABEL_OFFSET = {"CP7": (5, 11)}


def zone_at(x, y):
    return next((z[0] for z in ZONES if z[1] <= x <= z[3] and z[2] <= y <= z[4]), "Fuera del plano")


def route_info(rid):
    """Lo mismo que muestra MissionsScreen: puntos, metros y duración estimada."""
    name, ids = ROUTES[rid]
    pts = [CHECKPOINTS[i] for i in ids]
    length = sum(math.hypot(b[1] - a[1], b[2] - a[2]) for a, b in zip(pts, pts[1:]))
    count = sum(1 for i in ids if i != "BASE")
    secs = round(length / SPEED_MPS + count * DWELL_SECONDS)
    m, s = divmod(secs, 60)
    dur = f"{s} s" if m == 0 else f"{m} min" if s == 0 else f"{m} min {s} s"
    names = [CHECKPOINTS[i][0] for i in ids if i != "BASE"]
    return dict(id=rid, name=name, count=count, length=round(length), duration=dur, names=names)


def facility_map(cid, x, y, w, route=None, verified=(), current=None, robot=None, stale=False):
    """FacilityMap.kt: zonas, ruta activa, puntos de control y robot con su rumbo. Gris si los datos son antiguos."""
    s = w / FACILITY_W
    h = FACILITY_H * s
    grey = C["grey"]
    col = dict(line=C["line"], label=C["muted"], restricted=C["red"], route=C["blue"], verified=C["green"],
               pending=C["ink"], robot=C["blue"])
    if stale:
        col = dict(line=C["line"], label=grey, restricted=grey, route=grey, verified=grey, pending=grey, robot=grey)

    def P(mx, my):
        return x + mx * s, y + my * s

    out = [clip_rect(cid, x, y, w, h, 12), f'<g clip-path="url(#{cid})">', rect(x, y, w, h, 0, C["raised"])]
    zones = []
    for name, left, top, right, bottom, restricted in ZONES:
        zx, zy = P(left, top)
        zw, zh = (right - left) * s, (bottom - top) * s
        if restricted:
            zones += [rect(zx, zy, zw, zh, 0, col["restricted"], fill_opacity=0.12),
                      rect(zx, zy, zw, zh, 0, "none", col["restricted"], 1.5, dash="6 4")]
        else:
            zones.append(rect(zx, zy, zw, zh, 0, "none", col["line"], 1))
        label = "\n".join(wrap(name, 8, zw - 4))
        zones.append(text(zx + 2, zy + 9, label, 8, 400, col["restricted"] if restricted else col["label"], lh=10))
    out.append(g(zones, "Zonas"))
    ids = ROUTES[route][1] if route else []
    if len(ids) > 1:
        d = "M" + " L".join("{:.1f} {:.1f}".format(*P(*CHECKPOINTS[i][1:])) for i in ids)
        out.append(g(path(d, "none", col["route"], 2, opacity=0.7), "Ruta"))
    cps = []
    for key, (name, mx, my) in CHECKPOINTS.items():
        px, py = P(mx, my)
        if key == "BASE":
            cps.append(rect(px - 4, py - 4, 8, 8, 0, col["route"]))
        elif key in verified:
            cps.append(circle(px, py, 4, col["verified"]))
        elif key in ids:
            cps.append(circle(px, py, 4, "none", col["pending"], 1.5))
        else:
            cps.append(circle(px, py, 2.8, col["label"], opacity=0.5))
        if key == current:
            cps.append(circle(px, py, 8, "none", col["route"], 1.5))
        dx, dy = LABEL_OFFSET.get(key, (5, 3))
        cps.append(text(px + dx, py + dy, key, 8, 400, col["label"]))
    out.append(g(cps, "Puntos de control"))
    if robot:
        rx, ry, heading = robot
        cx, cy = P(rx, ry)
        a = math.radians(heading)
        dx, dy = math.cos(a), math.sin(a)
        nx, ny = -dy, dx
        tx, ty = cx + dx * 18, cy + dy * 18
        bx, by = tx - dx * 5, ty - dy * 5
        head = f"M{tx:.1f} {ty:.1f} L{bx + nx * 3:.1f} {by + ny * 3:.1f} L{bx - nx * 3:.1f} {by - ny * 3:.1f} Z"
        out.append(g([line(cx, cy, tx, ty, col["robot"], 2), path(head, col["robot"]),
                      circle(cx, cy, 12, col["robot"], opacity=0.25), circle(cx, cy, 6, col["robot"])], "Robot"))
    out.append("</g>")
    return g(out, "Facility map"), h


# ------------------------------------------------------- barra de órdenes ----
STOP_DISCLAIMER = "Detención simulada por software. No reemplaza la parada de emergencia física."
STALE_WARNING = "Sin datos recientes: la orden puede no llegar"


def hold_to_stop(x, y, w, progress=0.0, warning=None, status=None, touch=False):
    """HoldToStopButton: anillo de progreso alrededor del botón STOP y la aclaración al lado."""
    tx, tw_ = x + 84, w - 84
    col, cy = [], y
    t, th = para(tx, cy, "Mantené apretado 1,5 s para detener", "titleSmall", C["ink"], tw_)
    col.append(t)
    cy += th + 2
    t, th = para(tx, cy, STOP_DISCLAIMER, "bodySmall", C["muted"], tw_)
    col.append(t)
    cy += th
    if warning:
        t, th = para(tx, cy + 2, warning, "bodySmall", C["amber"], tw_)
        col.append(t)
        cy += th + 2
    if status:
        chip, _ = status_chip(tx, cy + 2, *status)
        col.append(chip)
        cy += 24
    h = max(72, cy - y)
    bx, by = x + 36, y + h / 2
    ring = [circle(bx, by, 34, "none", C["red"], 4, opacity=0.3)]
    if progress > 0:
        ring.append(path(arc(bx, by, 34, progress), "none", C["ink"], 4))
    btn = [circle(bx, by, 28, C["red"]), text(bx, by + 5, "STOP", 14, 700, C["white"], SANS, "middle")]
    if touch:
        btn.append(circle(bx + 6, by + 8, 20, C["white"], opacity=0.22))
    return g([g(ring, "Progreso"), g(btn, "Botón STOP"), g(col, "Texto")], "Hold to stop"), h


def slot(label, kind="filled", enabled=True, reason=None, warning=None, status=None):
    return dict(label=label, kind=kind, enabled=enabled, reason=reason, warning=warning, status=status)


def command_slot(x, y, w, s):
    lines = wrap(s["label"], 14, w - 12, 500)
    bh = max(40, 16 + 20 * len(lines))
    kind = s["kind"]
    if not s["enabled"]:
        kind = "disabled-outlined" if kind == "danger" else "disabled"
    out = [button(x, y, w, s["label"], kind, bh, lines=lines)]
    cy = y + bh
    note = s["reason"] if not s["enabled"] and s["reason"] else s["warning"]
    if note:
        color = C["muted"] if not s["enabled"] else C["amber"]
        t, th = para(x + w / 2, cy + 4, note, "bodySmall", color, w, "middle")
        out.append(t)
        cy += 4 + th
    if s["status"]:
        label, tone = s["status"]
        chip, _ = status_chip(x + w / 2, cy + 4, label, tone, "middle", w)
        out.append(chip)
        cy += 4 + 6 + 16 * len(wrap(label, 12, w - 20, 500))
    return g(out, "Command slot/" + s["label"]), cy - y


def command_bar(slots, stop=None):
    """CommandBar.kt: órdenes de la misión arriba y la detención aparte, separada y en rojo.
    Devuelve (svg, alto); se dibuja pegada a la barra de navegación."""
    stop = stop or {}
    sw = (W - 24 - 16) / 3
    parts = [command_slot(12 + i * (sw + 8), 8, sw, s) for i, s in enumerate(slots)]
    row_h = max(h for _, h in parts)
    hs, sh = hold_to_stop(12, 8 + row_h + 25, W - 24, **stop)
    h = 8 + row_h + 8 + 1 + 8 + sh + 8
    y = 720 - h
    body = [rect(0, 0, W, h, 0, C["surface"]), line(0, 0.5, W, 0.5, C["line"], 1, opacity=0.6)] + [p for p, _ in parts]
    body += [line(12, 8 + row_h + 8.5, W - 12, 8 + row_h + 8.5, C["line"]), hs]
    return g(body, "Command bar", f"translate(0 {num(float(y))})"), h


# ---------------------------------------------------------------- diálogos ----
def text_button(x_right, y, label, color=C["blue"], enabled=True):
    w = tw(label, 14, 500) + 24
    t = text(x_right - w / 2, y + 25, label, 14, 500, color if enabled else C["ink"], SANS, "middle",
             opacity=None if enabled else 0.38)
    return g([t], "Text button/" + label), w


def dialog_box(title, items, confirm=None, dismiss=None, warn_icon=False, confirm_color=C["blue"],
               confirm_enabled=True, x=24, w=312):
    """Contenedor del AlertDialog de M3. items: funciones (x, y, w) -> (svg, alto) separadas 12 dp.
    Devuelve (svg, alto) con el borde superior en y = 0."""
    inner = w - 48
    parts, cy = [], 24
    if warn_icon:
        parts.append(icon("warning", x + w / 2 - 12, cy, 24, C["red"]))
        cy += 24 + 16
    t, th = para(x + (w / 2 if warn_icon else 24), cy, title, "headlineSmall", C["ink"], inner,
                 "middle" if warn_icon else "start")
    parts.append(t)
    cy += th + 16
    for i, fn in enumerate(items):
        svg, h = fn(x + 24, cy, inner)
        parts.append(svg)
        cy += h + (12 if i < len(items) - 1 else 0)
    cy += 24
    bx = x + w - 24
    if confirm:
        b, bw = text_button(bx, cy, confirm, confirm_color, confirm_enabled)
        parts.append(b)
        bx -= bw + 8
    if dismiss:
        b, bw = text_button(bx, cy, dismiss)
        parts.append(b)
    h = cy + 40 + 24
    return g([rect(x, 0, w, h, 28, C["raised"])] + parts, "Dialog/" + title), h


def alert_dialog(*args, **kw):
    """Diálogo centrado sobre la pantalla, con el scrim detrás."""
    box, h = dialog_box(*args, **kw)
    return g([scrim(0.55), g(box, transform=f"translate(0 {round((H - h) / 2)})")], "Dialog")


def p_item(s, style="bodyMedium", fill=C["muted"], italic=False):
    return lambda x, y, w: para(x, y, s, style, fill, w, italic=italic)


def btn_item(label, kind="filled", color=None):
    return lambda x, y, w: (button(x, y, button_w(label), label, kind, color=color), 40)


def decision_options(options, selected=None, enabled=True):
    """Opciones con radio del IncidentDecisionDialog: etiqueta y qué hace el robot."""
    def fn(x, y, w):
        out, cy = [], y
        for i, (label, expl) in enumerate(options):
            cy += 4
            t, th = para(x + 32, cy, label, "titleSmall", C["muted"], w - 32)
            d, dh = para(x + 32, cy + th, expl, "bodySmall", C["muted"], w - 32)
            out += [radio(x + 10, cy + (th + dh) / 2, label == selected, enabled), t, d]
            cy += th + dh + 4 + (4 if i < len(options) - 1 else 0)
        return g(out, "Decision options"), cy - y
    return fn


OBSTACLE_OPTIONS = [
    ("Esperar", "El robot espera a que se libere el paso y sigue la ronda."),
    ("Saltear el punto", "Deja este punto sin verificar y sigue con el siguiente."),
    ("Volver a la base", "Interrumpe la ronda y vuelve a la base."),
]
PERSON_OPTIONS = [
    ("Intruso", "Se activa el procedimiento de seguridad; el robot queda en pausa."),
    ("Empleado autorizado", "Se registra la presencia y el robot sigue la ronda."),
    ("Falsa alarma", "Queda registrada para ajustar la ruta; el robot sigue."),
]
PRIVACY_NOTE = "No se guardan imágenes; se registra zona, hora y tu clasificación"


# ================================================================ PANTALLAS ==
SCREENS = []  # (id, slug, nombre, sección, fn)


def screen(sid, slug, name, section):
    def deco(fn):
        SCREENS.append((sid, slug, name, section, fn))
        return fn
    return deco


def by_id(sid):
    return next(e for e in SCREENS if e[0] == sid)


def dec(v):
    return f"{v:.1f}".replace(".", ",")


class Column:
    """Apila bloques como un Column con spacedBy: cada add recibe una función y -> (svg, alto)."""

    def __init__(self, y, gap=12):
        self.y, self.gap, self.out, self.first = y, gap, [], True

    def add(self, fn):
        if not self.first:
            self.y += self.gap
        svg, h = fn(self.y)
        self.out.append(svg)
        self.y += h
        self.first = False
        return self


# Telemetría de ejemplo, coherente entre pantallas (Ronda completa, 3 de 8 puntos)
PATROL = dict(
    state="Patrullando", state_tone="ok", battery=64, minutes=72, charging=False, battery_tone="ok",
    conn=("Conectado", "ok"), latency=85, rssi=-62, stale=False, age=0, clock="03:12:41",
    mission=dict(route="Ronda completa", pct=42, toward="Laboratorio de informática", verified=3, total=8),
    pose=(24.0, 5.0, 350), speed=0.8, people=0, doors=0, service_verified=3,
    route="ronda-completa", verified={"CP1", "CP2", "CP3"}, current="CP4",
    incident=None, battery_warning=None, badge=0, status_clock="03:12",
    slots=[slot("Pausar"), slot("Cancelar", "danger"), slot("Volver a la base", "tonal")], stop=None,
)
DOCKED = dict(
    PATROL, state="En base", battery=100, charging=True, latency=80, rssi=-60, clock="03:05:02", mission=None,
    pose=(3.0, 3.0, 0), speed=0.0, service_verified=0, route=None, verified=set(), current=None, status_clock="03:05",
    slots=[slot("Elegir ronda"), slot("Cancelar", "danger", False, "No hay una misión en curso"),
           slot("Volver a la base", "tonal", False, "El robot ya está en la base")],
)


def stale_slots(slots, status=None):
    out = [dict(s, warning=STALE_WARNING) if s["enabled"] else s for s in slots]
    if status:
        out[0] = dict(out[0], status=status)
    return out


def supervision_cards(t):
    stale = t["stale"]
    data = "stale" if stale else "neutral"
    m = t["mission"]
    x, y, heading = t["pose"]
    cards = [
        dict(title="Batería", value=f"{t['battery']} %", mono=True,
             detail="Cargando" if t["charging"] else f"{t['minutes']} min restantes",
             tone="stale" if stale else t["battery_tone"]),
        dict(title="Estado", value=t["state"], tone="stale" if stale else t["state_tone"]),
        dict(title="Misión", value=m["route"] if m else "Sin misión activa", tone=data,
             detail=f"{m['pct']} % · hacia {m['toward']}" if m else None, progress=m["pct"] / 100 if m else None),
        dict(title="Ubicación", value=zone_at(x, y), detail=f"x {dec(x)} m · y {dec(y)} m", tone=data),
        dict(title="Movimiento", value=f"{dec(t['speed'])} m/s", detail=f"Rumbo {heading}°", tone=data, mono=True),
        dict(title="Última actualización", value=f"hace {t['age']} s", detail=t["clock"], tone=data),
        dict(title="Puntos verificados", value=f"{m['verified']}/{m['total']}" if m else str(t["service_verified"]),
             detail=None if m else "Última ronda", tone=data, mono=True),
        dict(title="Personas en zona restringida", value=str(t["people"]), mono=True,
             tone="stale" if stale else "error" if t["people"] else "neutral"),
        dict(title="Puertas abiertas", value=str(t["doors"]), mono=True,
             tone="stale" if stale else "warning" if t["doors"] else "neutral"),
    ]
    return cards


def supervision(sid, t, overlay=None):
    """SupervisionScreen dentro del Scaffold: avisos, plano, tarjetas y barra de órdenes."""
    bar, bar_h = command_bar(t["slots"], t["stop"])
    top = 720 - bar_h
    col = Column(104)
    x, w = 16, 328
    if t["stale"]:
        msg = f"Sin datos hace {t['age']} s. Las órdenes pueden no llegar"
        col.add(lambda y: notice(x, y, w, msg, C["red"], C["grey"], 0.2))

    def conn_row(y):
        chip, cw = status_chip(x, y, *t["conn"])
        out = [chip]
        if not t["stale"]:
            out.append(text(x + cw + 8, y + 15, f"Latencia {t['latency']} ms · {t['rssi']} dBm", 12, 400, C["muted"]))
        return g(out, "Connection row"), 22
    col.add(conn_row)
    if t["incident"]:
        msg, zone = t["incident"]
        col.add(lambda y: notice(x, y, w, "Incidente: requiere tu decisión", C["red"], actions=[
            p_item(f"{msg} ({zone})", "bodySmall", C["ink"]), btn_item("Decidir", color=C["red"])]))
    if t["battery_warning"]:
        critical = t["battery_warning"] == "critical"
        color = C["red"] if critical else C["amber"]
        msg = ("Batería crítica: el robot vuelve a la base y no se puede reanudar." if critical
               else "Batería baja: conviene volver a la base.")
        col.add(lambda y: notice(x, y, w, msg, color, actions=[btn_item("Volver a la base", color=color)]))
    col.add(lambda y: facility_map(f"{sid}-map", x, y, w, t["route"], t["verified"], t["current"],
                                   t["pose"], t["stale"]))
    col.add(lambda y: telemetry_grid(x, y, w, supervision_cards(t)))
    content = [clip_rect(f"{sid}-scroll", 0, 88, W, top - 88), g(col.out, "Contenido", clip=f"{sid}-scroll")]
    out = [status_bar(t["status_clock"]), top_app_bar()] + content + [bar, nav_bar(0, t["badge"])]
    if overlay:
        out.append(overlay)
    return out


def robot_card(x, y, w, name, note, available):
    """RobotItem: los robots no disponibles quedan atenuados y sin acción, con el motivo a la vista."""
    label, tone = ("Disponible", "ok") if available else ("No disponible", "stale")
    cw = tw(label, 12, 500) + 20
    tx, tw_ = x + 16, w - 32 - cw - 12
    t1, h1 = para(tx, y + 16, name, "titleMedium", C["ink"], tw_)
    t2, h2 = para(tx, y + 16 + h1 + 2, "Unitree Go2", "bodySmall", C["muted"], tw_)
    t3, h3 = para(tx, y + 16 + h1 + 2 + h2 + 2, note, "bodySmall", C["ink"], tw_)
    h = 16 + h1 + 2 + h2 + 2 + h3 + 16
    chip, _ = status_chip(x + w - 16, y + h / 2 - 11, label, tone, "end")
    return g([card(x, y, w, h), t1, t2, t3, chip], "Robot/" + name, opacity=None if available else 0.55), h


# ----------------------------------------------------------- A. Ingreso ------
@screen("01", "seleccion-de-robot", "Selección de robot · Vigía-02 deshabilitado", "A · Ingreso")
def s_robot_select(sid):
    out = [status_bar("22:03")]
    col = Column(40)
    col.add(lambda y: para(16, y, "Selección de robot", "headlineSmall", C["ink"]))
    col.add(lambda y: para(16, y, "Elegí el robot que vas a supervisar en la ronda.", "bodyMedium", C["muted"]))

    col.add(lambda y: robot_card(16, y, 328, "Vigía-01", "Simulado", True))
    col.add(lambda y: robot_card(16, y, 328, "Vigía-02", "Fuera de servicio: batería en mantenimiento", False))
    return out + col.out + [gesture_bar()]


# -------------------------------------------------------- B. Supervisión ----
@screen("02", "supervision-en-base", "Supervisión · robot en base", "B · Supervisión")
def s_docked(sid):
    return supervision(sid, DOCKED)


@screen("03", "supervision-patrullando", "Supervisión · patrullando", "B · Supervisión")
def s_patrol(sid):
    return supervision(sid, PATROL)


@screen("09", "bateria-baja", "Supervisión · batería baja", "B · Supervisión")
def s_low_battery(sid):
    return supervision(sid, dict(PATROL, battery=22, minutes=26, battery_tone="warning", battery_warning="low",
                                 badge=1, clock="03:16:20", status_clock="03:16"))


# ------------------------------------------------------------ C. Control ----
@screen("04", "confirmar-volver-a-la-base", "Control · confirmar Volver a la base", "C · Control")
def s_confirm_return(sid):
    dialog = alert_dialog("¿Volver a la base?",
                          [p_item("El robot interrumpe lo que está haciendo y regresa a la base de carga.")],
                          "Volver a la base", "Descartar")
    return supervision(sid, PATROL, dialog)


@screen("05", "detencion-mantenida", "Control · detención mantenida", "C · Control")
def s_hold_stop(sid):
    return supervision(sid, dict(PATROL, stop=dict(progress=0.62, touch=True)))


@screen("06a", "orden-pendiente", "Orden · pendiente", "C · Control")
def s_cmd_pending(sid):
    slots = [slot("Pausar"), slot("Cancelar", "danger"),
             slot("Volver a la base", "tonal", False, "Hay una orden igual pendiente", status=("Pendiente", "neutral"))]
    return supervision(sid, dict(PATROL, slots=slots))


@screen("06b", "orden-confirmada", "Orden · confirmada", "C · Control")
def s_cmd_confirmed(sid):
    slots = [slot("Pausar", enabled=False, reason="Solo se puede pausar durante la patrulla"),
             slot("Cancelar", "danger", False, "No hay una misión en curso"),
             slot("Volver a la base", "tonal", False, "El robot ya está regresando", status=("Confirmada", "ok"))]
    mission = dict(PATROL["mission"], toward="Base de carga")
    return supervision(sid, dict(PATROL, state="Regresando", mission=mission, pose=(22.5, 5.0, 180), current="BASE",
                                 clock="03:13:02", status_clock="03:13", slots=slots))


@screen("06c", "orden-rechazada", "Orden · rechazada", "C · Control")
def s_cmd_rejected(sid):
    slots = [slot("Pausar"), slot("Cancelar", "danger"),
             slot("Volver a la base", "tonal", status=("Rechazada: Camino bloqueado", "error"))]
    return supervision(sid, dict(PATROL, slots=slots))


@screen("06d", "orden-sin-respuesta", "Orden · sin respuesta", "C · Control")
def s_cmd_no_response(sid):
    return supervision(sid, dict(DISCONNECTED, age=18, slots=stale_slots(PATROL["slots"], ("Sin respuesta", "warning"))))


# ----------------------------------------------------------- D. Misiones ----
def missions(sid, start_enabled=True, reason=None):
    col = Column(104)
    col.add(lambda y: para(16, y, "Rondas disponibles", "titleMedium", C["ink"]))
    if reason:
        col.add(lambda y: notice(16, y, 328, f"No se puede iniciar: {reason}", C["amber"]))

    def route_card(rid):
        r = route_info(rid)

        def fn(y):
            parts, cy = [], y + 12
            for s, style, fill in [
                (r["name"], "titleSmall", C["ink"]),
                (f"{r['count']} puntos de control · {r['length']} m · Duración estimada: {r['duration']}",
                 "bodyMedium", C["ink"]),
                (" → ".join(["Base"] + r["names"] + ["Base"]), "bodySmall", C["muted"]),
            ]:
                t, th = para(28, cy, s, style, fill, 304)
                parts.append(t)
                cy += th + 8
            parts.append(button(28, cy, button_w("Iniciar"), "Iniciar", "filled" if start_enabled else "disabled"))
            h = cy + 40 + 12 - y
            return g([card(16, y, 328, h)] + parts, f"Route card/{r['name']}"), h
        return fn
    for rid in ROUTES:
        col.add(route_card(rid))
    content = [clip_rect(f"{sid}-scroll", 0, 88, W, 632), g(col.out, "Contenido", clip=f"{sid}-scroll")]
    return [status_bar("03:06"), top_app_bar()] + content + [nav_bar(1)]


@screen("07", "misiones", "Misiones · Iniciar habilitado", "D · Misiones")
def s_missions(sid):
    return missions(sid)


@screen("08", "misiones-bateria-baja", "Misiones · batería baja, Iniciar deshabilitado", "D · Misiones")
def s_missions_low(sid):
    return missions(sid, False, "Batería por debajo del 30 %")


# -------------------------------------------------------- E. Desconexión ----
DISCONNECTED = dict(PATROL, stale=True, age=12, conn=("Sin datos", "error"), clock="03:14:05", status_clock="03:14",
                    stop=dict(warning=STALE_WARNING))
DISCONNECTED["slots"] = stale_slots(PATROL["slots"])


@screen("10", "desconexion", "Desconexión · datos antiguos", "E · Desconexión")
def s_disconnected(sid):
    return supervision(sid, DISCONNECTED)


@screen("11", "reconexion", "Reconexión · datos al día", "E · Desconexión")
def s_reconnected(sid):
    slots = [slot("Pausar", status=("Sin respuesta", "warning")), slot("Cancelar", "danger"),
             slot("Volver a la base", "tonal")]
    mission = dict(PATROL["mission"], pct=55, toward="Biblioteca", verified=4)
    return supervision(sid, dict(PATROL, mission=mission, pose=(32.0, 8.0, 63), verified={"CP1", "CP2", "CP3", "CP4"},
                                 current="CP5", latency=140, rssi=-71, service_verified=4, clock="03:14:31",
                                 status_clock="03:14", slots=slots))


# ------------------------------------------------- F. Incidentes y alertas ----
INCIDENT = dict(
    PATROL, state="Incidente", state_tone="error", battery=41, minutes=46, clock="03:18:03", status_clock="03:18",
    mission=dict(route="Ronda completa", pct=84, toward="Pasillo de aulas B", verified=7, total=8),
    pose=(17.0, 15.0, 188), speed=0.0, service_verified=7, current="CP8", latency=110, rssi=-68, doors=0,
    verified={"CP1", "CP2", "CP3", "CP4", "CP5", "CP6", "CP7"}, incident=("Obstáculo bloquea el paso", "Pasillo B"),
    badge=1, slots=[slot("Pausar", enabled=False, reason="Solo se puede pausar durante la patrulla"),
                    slot("Cancelar", "danger"), slot("Volver a la base", "tonal")],
)
PERSON = dict(
    INCIDENT, battery=58, minutes=64, clock="03:31:12", status_clock="03:31",
    mission=dict(route="Ronda completa", pct=40, toward="Laboratorio de informática", verified=3, total=8),
    pose=(28.5, 4.2, 350), service_verified=3, current="CP4", verified={"CP1", "CP2", "CP3"}, people=1, badge=2,
    incident=("Persona detectada en zona restringida", "Laboratorio"),
)


@screen("12", "incidente-obstaculo", "Incidente · obstáculo con banner", "F · Incidentes y alertas")
def s_incident(sid):
    return supervision(sid, INCIDENT)


@screen("13", "decision-obstaculo", "Incidente · diálogo de decisión", "F · Incidentes y alertas")
def s_decide_obstacle(sid):
    dialog = alert_dialog("Obstáculo en el camino", [
        p_item("Obstáculo bloquea el paso · Pasillo B"),
        decision_options(OBSTACLE_OPTIONS, "Saltear el punto"),
    ], "Enviar decisión", "Más tarde", warn_icon=True)
    return supervision(sid, INCIDENT, dialog)


@screen("14", "persona-en-zona-restringida", "Incidente · persona en zona restringida", "F · Incidentes y alertas")
def s_decide_person(sid):
    dialog = alert_dialog("Persona en zona restringida", [
        p_item("Persona detectada en zona restringida · Laboratorio"),
        p_item("El robot dijo: \"Zona restringida. Seguridad fue notificada.\"", italic=True),
        decision_options(PERSON_OPTIONS),
        p_item(PRIVACY_NOTE, "bodySmall"),
    ], "Enviar decisión", "Más tarde", warn_icon=True, confirm_enabled=False)
    return supervision(sid, PERSON, dialog)


def alert_card(x, y, w, severity, tone, kind, msg, meta, decide=False, ack=True, resolution=None, faded=False):
    """AlertCard: borde y chip con el color de la severidad, acciones según el estado."""
    inner = w - 24
    parts, cy = [], y + 12
    chip, cw = status_chip(x + 12, cy + 1, severity, tone)
    t, th = para(x + 12 + cw + 8, cy, kind, "titleSmall", C["ink"], inner - cw - 8)
    parts += [chip, t]
    cy += max(24, th) + 8
    for s, style, fill in [(msg, "bodyMedium", C["ink"]), (meta, "bodySmall", C["muted"])]:
        t, th = para(x + 12, cy, s, style, fill, inner)
        parts.append(t)
        cy += th + 8
    if resolution:
        t, th = para(x + 12, cy, resolution, "labelLarge", C["ink"], inner)
        parts.append(t)
        cy += th + 8
    bx = x + 12
    if decide:
        parts.append(button(bx, cy, button_w("Decidir"), "Decidir", color=C["red"]))
        bx += button_w("Decidir") + 8
    if ack:
        parts.append(button(bx, cy, button_w("Reconocer"), "Reconocer", "outlined"))
    if decide or ack:
        cy += 40 + 8
    h = cy - 8 + 12 - y
    border = rect(x + 0.5, y + 0.5, w - 1, h - 1, 11.5, "none", TONE[tone], 1, opacity=0.7)
    return g([card(x, y, w, h), border] + parts, f"Alert card/{kind}", opacity=0.6 if faded else None), h


@screen("15", "alertas", "Alertas · activas y reconocidas", "F · Incidentes y alertas")
def s_alerts(sid):
    col = Column(104)
    col.add(lambda y: para(16, y, "Activas (2)", "titleMedium", C["ink"]))
    col.add(lambda y: alert_card(16, y, 328, "Crítica", "error", "Persona en zona restringida",
                                 "Persona detectada en zona restringida", "Laboratorio · 03:31:12", decide=True))
    col.add(lambda y: alert_card(16, y, 328, "Advertencia", "warning", "Puerta abierta", "Puerta abierta detectada",
                                 "Salida de emergencia · 03:29:47"))
    col.add(lambda y: para(16, y, "Reconocidas y resueltas", "titleMedium", C["ink"]))
    col.add(lambda y: alert_card(16, y, 328, "Crítica", "error", "Obstáculo en el camino", "Obstáculo bloquea el paso",
                                 "Pasillo B · 03:18:03", ack=False, resolution="Decisión: Saltear el punto", faded=True))
    content = [clip_rect(f"{sid}-scroll", 0, 88, W, 632), g(col.out, "Contenido", clip=f"{sid}-scroll")]
    return [status_bar("03:32"), top_app_bar()] + content + [nav_bar(2, 2)]


# ---------------------------------------------------------- G. Historial ----
MISSIONS = [
    dict(id="m-0004", route="Ronda completa", result=("En curso", "neutral"), start="16/11 03:10", dur=None,
         verified="3/8", people=1, doors=1),
    dict(id="m-0003", route="Ronda completa", result=("Completada", "ok"), start="16/11 02:45", dur="2 min 40 s",
         verified="7/8", people=0, doors=0),
    dict(id="m-0002", route="Zonas críticas", result=("Vuelta a la base", "warning"), start="16/11 01:30",
         dur="41 s", verified="1/2", people=0, doors=0),
    dict(id="m-0001", route="Ronda perimetral", result=("Completada", "ok"), start="16/11 00:15", dur="1 min 13 s",
         verified="3/3", people=0, doors=1),
]
TIMELINE = [  # hora, tipo, tono, mensaje, zona
    ("02:45:10", "Misión", "neutral", "Misión m-0003 iniciada: Ronda completa", None),
    ("02:45:20", "Punto de control", "neutral", "Punto verificado: Hall de entrada", "Hall de entrada"),
    ("02:46:31", "Punto de control", "neutral", "Punto verificado: Salida de emergencia", "Salida de emergencia"),
    ("02:46:40", "Alerta", "error", "Obstáculo bloquea el paso", "Pasillo B"),
    ("02:46:58", "Decisión", "neutral", "Decisión del guardia: Saltear el punto", "Pasillo B"),
    ("02:46:58", "Orden", "neutral", "Resolver incidente: Confirmada", None),
    ("02:47:50", "Misión", "neutral", "Misión m-0003 completada: 7/8 puntos", None),
]


def mission_card(x, y, w, m, timeline=None):
    inner = w - 24
    parts, cy = [], y + 12
    chip, cw = status_chip(x + w - 12, cy + 1, *m["result"], anchor="end")
    t, th = para(x + 12, cy, m["route"], "titleSmall", C["ink"], inner - cw - 8)
    parts += [chip, t]
    cy += max(24, th) + 6
    meta = " · ".join(v for v in (m["id"], m["start"], m["dur"]) if v)
    for s, style, fill in [(meta, "bodySmall", C["muted"]),
                           (f"Puntos verificados {m['verified']} · Personas {m['people']} · "
                            f"Puertas abiertas {m['doors']}", "bodyMedium", C["ink"])]:
        t, th = para(x + 12, cy, s, style, fill, inner)
        parts.append(t)
        cy += th + 6
    if timeline:
        cy += 4
        parts.append(line(x + 12, cy, x + w - 12, cy, C["line"]))
        cy += 4 + 6
        for hour, kind, tone, msg, zone in timeline:
            parts.append(text(x + 12, cy + 12, hour, 12, 500, C["muted"], MONO))
            tx = x + 12 + 72
            t, th = para(tx, cy, kind, "labelMedium", TONE[tone], inner - 72)
            parts.append(t)
            ey = cy + th
            t, th = para(tx, ey, msg, "bodySmall", C["ink"], inner - 72)
            parts.append(t)
            ey += th
            if zone:
                t, th = para(tx, ey, zone, "bodySmall", C["muted"], inner - 72)
                parts.append(t)
                ey += th
            cy = ey + 6
    h = cy - 6 + 12 - y
    return g([card(x, y, w, h)] + parts, f"Mission card/{m['id']}"), h


def history(sid, expanded=None):
    col = Column(104)

    def header(y):
        out = [text(16, y + 26, "Rondas guardadas", 16, 500, C["ink"]),
               text(344 - 12, y + 25, "Borrar historial", 14, 500, C["red"], SANS, "end")]
        cx = 16
        for label, on in [("Todas", True), ("Con incidentes", False)]:
            fw = tw(label, 14, 500) + 32
            out += [rect(cx, y + 48, fw, 32, 8, C["blueDeep"] if on else "none", None if on else C["line"]),
                    text(cx + fw / 2, y + 69, label, 14, 500, C["ink"] if on else C["muted"], SANS, "middle")]
            cx += fw + 8
        return g(out, "Header"), 80
    col.add(header)
    for m in MISSIONS:
        col.add(lambda y, m=m: mission_card(16, y, 328, m, TIMELINE if m["id"] == expanded else None))
    content = [clip_rect(f"{sid}-scroll", 0, 88, W, 632), g(col.out, "Contenido", clip=f"{sid}-scroll")]
    return [status_bar("03:33"), top_app_bar()] + content + [nav_bar(3, 2)]


@screen("16", "historial", "Historial · rondas guardadas", "G · Historial")
def s_history(sid):
    return history(sid)


@screen("17", "detalle-de-ronda", "Historial · detalle de ronda", "G · Historial")
def s_history_detail(sid):
    return history(sid, "m-0003")


# --------------------------------------------------------------- H. Demo ----
@screen("18", "panel-de-simulacion", "Panel de simulación (solo demo)", "H · Demo")
def s_simulation(sid):
    top = 64
    out = supervision(sid, PATROL)
    sheet = [scrim(0.32), rect(0, top, W, H - top + 28, 28, C["surface"]), rect(164, top + 22, 32, 4, 2, C["muted"], opacity=0.4)]
    col = Column(top + 48, 16)
    x, w = 16, 328

    def title(y):
        a, ah = para(x, y, "Panel de simulación (solo demo)", "titleMedium", C["ink"], w)
        b, bh = para(x, y + ah, "Fuerza situaciones del robot simulado. No forma parte de la app del guardia.",
                     "bodySmall", C["muted"], w)
        return a + b, ah + bh

    def control_row(label, sub, control_w, control, control_h=40):
        """ControlRow: título y detalle a la izquierda, el control centrado a la derecha."""
        def fn(y):
            tw_ = w - control_w - 12
            _, ah = para(x, 0, label, "bodyLarge", C["ink"], tw_)
            bh = para(x, 0, sub, "bodySmall", C["muted"], tw_)[1] if sub else 0
            h = max(ah + bh, control_h)
            ty = y + (h - ah - bh) / 2
            parts = [para(x, ty, label, "bodyLarge", C["ink"], tw_)[0]]
            if sub:
                parts.append(para(x, ty + ah, sub, "bodySmall", C["muted"], tw_)[0])
            parts.append(control(x + w - control_w, y + (h - control_h) / 2))
            return g(parts, "Control/" + label), h
        return fn

    def outlined(label, enabled=True):
        bw = button_w(label)
        return bw, lambda cx, cy: button(cx, cy, bw, label, "outlined" if enabled else "disabled-outlined")

    def divider(y):
        return line(x, y, x + w, y, C["line"]), 1

    def battery(y):
        out_ = [para(x, y, "Batería: 64 %", "titleSmall", C["ink"], w)[0]]
        sy = y + 20 + 8 + 10
        out_ += [rect(x, sy - 2, w * 0.64, 4, 2, C["blue"]), rect(x + w * 0.64, sy - 2, w * 0.36, 4, 2, C["line"]),
                 circle(x + w * 0.64, sy, 10, C["blue"])]
        bx = x
        for q in ("24 %", "14 %"):
            bw = button_w(q)
            out_.append(button(bx, sy + 18, bw, q, "outlined"))
            bx += bw + 8
        return g(out_, "Battery control"), sy + 18 + 40 - y

    rej_w, rej = outlined("Rechazar")
    dis_w, dis = outlined("Disparar")
    col.add(title)
    col.add(lambda y: para(x, y, "Robot: Patrullando · Batería: 64 %", "bodyMedium", C["ink"], w))
    col.add(control_row("Conexión con el robot", "Apagala para simular la pérdida de enlace", 52,
                        lambda cx, cy: switch(cx, cy, True), 32))
    col.add(control_row("Rechazar próxima orden", "La próxima orden que mandes vuelve rechazada", rej_w, rej))
    col.add(divider)
    col.add(battery)
    col.add(divider)
    col.add(lambda y: para(x, y, "Eventos", "titleSmall", C["ink"], w))
    col.gap = 12
    col.add(control_row("Obstáculo en el pasillo", "", dis_w, dis))
    col.add(control_row("Persona en zona restringida", "", dis_w, dis))
    col.add(control_row("Puerta abierta", "Salida de emergencia lateral", dis_w, dis))
    col.gap = 16
    col.add(divider)

    def speed_chips(cx, cy):
        out_ = []
        for label, on in [("x1", False), ("x5", True)]:
            out_ += [rect(cx, cy + 4, 44, 32, 8, C["blueDeep"] if on else "none", None if on else C["line"]),
                     text(cx + 22, cy + 25, label, 14, 500, C["ink"] if on else C["muted"], SANS, "middle")]
            cx += 52
        return g(out_, "Filter chips")
    col.add(control_row("Velocidad de la simulación", "x5 acelera la ronda para la demo", 96, speed_chips))
    return out + [g(sheet + col.out, "Bottom sheet/Panel de simulación")]


# ================================================================ TABLEROS ===
BOARD_BG = "#070C16"  # un poco más oscuro que la app para que los frames se despeguen del fondo


def thumb(sid, x, y, scale, caption=True, prefix="t"):
    """Miniatura de un frame (svg anidado y recortado) con su número y nombre debajo."""
    _, _, name, _, fn = by_id(sid)
    key = f"{prefix}{sid}x{int(x)}y{int(y)}"
    body = "".join(fn(key))
    w, h = W * scale, H * scale
    out = [rect(x - 1, y - 1, w + 2, h + 2, 18 * scale + 1, C["line"]),
           f'<svg x="{num(x)}" y="{num(y)}" width="{num(w)}" height="{num(h)}" viewBox="0 0 {W} {H}">'
           f'{clip_rect(key + "c", 0, 0, W, H, 28)}<g clip-path="url(#{key}c)">'
           f'<rect width="{W}" height="{H}" fill="{C["navy"]}"/>{body}</g></svg>']
    if caption:
        title, _, sub = name.partition(" · ")
        out.append(text(x, y + h + 22, f"{sid} · {title}", 14, 600, C["ink"]))
        if sub:
            out.append(text(x, y + h + 40, "\n".join(wrap(sub, 12, w + 60)), 12, 400, C["muted"], lh=16))
    return g(out, f"Frame {sid}")


def arrow(pts, label=None, color=C["blue"], dash=None, at=None):
    """Flecha ortogonal por los puntos dados; la etiqueta va en `at` o en la mitad del tramo más largo."""
    d = "M" + " L".join(f"{num(float(a))} {num(float(b))}" for a, b in pts)
    out = [path(d, "none", color, 2, dash=dash)]
    (ax, ay), (bx, by) = pts[-2], pts[-1]
    ang = math.atan2(by - ay, bx - ax)
    h1 = (bx - 10 * math.cos(ang - 0.45), by - 10 * math.sin(ang - 0.45))
    h2 = (bx - 10 * math.cos(ang + 0.45), by - 10 * math.sin(ang + 0.45))
    out.append(path(f"M{h1[0]:.1f} {h1[1]:.1f} L{bx} {by} L{h2[0]:.1f} {h2[1]:.1f}", "none", color, 2))
    if label:
        if at is None:
            segs = list(zip(pts, pts[1:]))
            (p1, p2) = max(segs, key=lambda s: abs(s[1][0] - s[0][0]) + abs(s[1][1] - s[0][1]))
            at = ((p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2)
        lines = label.split("\n")
        lw = max(tw(ln, 12, 600) for ln in lines) + 18
        lh = 8 + 16 * len(lines)
        mx, my = at
        out += [rect(mx - lw / 2, my - lh / 2, lw, lh, min(12, lh / 2), BOARD_BG, color, 1.5),
                text(mx, my - lh / 2 + 16, label, 12, 600, color, SANS, "middle", 16)]
    return g(out, "Flecha/" + (label or "").replace("\n", " "))


def pill(cx, cy, label, color=C["blue"], w=None):
    w = w or tw(label, 14, 600) + 36
    return g([rect(cx - w / 2, cy - 20, w, 40, 20, "none", color, 1.5),
              text(cx, cy + 5, label, 14, 600, color, SANS, "middle")], "Nodo/" + label)


def board_head(title, sub):
    return [text(60, 84, title, 40, 700, C["ink"]), text(60, 120, sub, 18, 400, C["muted"])]


def section_label(x, y, label, color=C["blue"]):
    return text(x, y, label.upper(), 13, 700, color, ls=1.2)


# --------------------------------------------------------- mapa de navegación
def board_navigation():
    s = 0.3
    tw_, th = W * s, H * s  # 108 x 240
    cols = lambda c: 60 + c * 250
    rows = lambda r: 220 + r * 350
    place = {"01": (0, 0), "02": (0, 1), "07": (0, 2), "03": (0, 3), "04": (0, 4), "06a": (0, 5), "06b": (0, 6),
             "08": (1, 2), "05": (1, 4), "06c": (1, 6),
             "16": (2, 0), "18": (2, 1), "09": (2, 4),
             "17": (3, 0), "10": (3, 4), "06d": (3, 5), "11": (3, 6),
             "12": (4, 4), "13": (4, 5),
             "14": (5, 4), "15": (5, 5)}
    pos = {sid: (cols(c), rows(r)) for sid, (r, c) in place.items()}

    def R(sid, f=0.5):
        x, y = pos[sid]
        return x + tw_ + 6, y + th * f

    def L(sid, f=0.5):
        x, y = pos[sid]
        return x - 6, y + th * f

    def T(sid, f=0.5):
        x, y = pos[sid]
        return x + tw_ * f, y - 6

    def B(sid, f=0.5):
        x, y = pos[sid]
        return x + tw_ * f, y + th + 52

    out = board_head("Mapa de navegación · Vigía",
                     "Todos los frames y cómo se llega a cada uno. Línea llena: acción del guardia. "
                     "Punteada: cambio que viene del robot o del sistema.")
    out += [section_label(60, 190, "Ingreso, misiones y control"), section_label(60, rows(2) - 30, "Pestañas y demo"),
            section_label(cols(4) + tw_ + 60, rows(2) - 30, "Advertencias, desconexión e incidentes")]
    out += [thumb(sid, x, y, s, prefix="n") for sid, (x, y) in pos.items()]
    sys_ = dict(color=C["muted"], dash="6 5")
    a = []
    a.append(arrow([R("01"), L("02")], "Vigía-01"))
    a.append(arrow([R("02"), L("07")], "Elegir ronda"))
    a.append(arrow([R("07"), L("03")], "Iniciar"))
    a.append(arrow([B("07"), T("08")], "Batería < 30 %", at=(cols(2) + tw_ / 2, rows(1) - 34), **sys_))
    a.append(arrow([R("03", 0.35), L("04", 0.35)], "Volver a\nla base"))
    a.append(arrow([R("04"), L("06a")], "Confirmar"))
    a.append(arrow([R("06a"), L("06b")], "Confirmada", **sys_))
    x6 = cols(5) + tw_ + 40
    a.append(arrow([R("06a", 0.8), (x6, rows(0) + th * 0.8), (x6, rows(1) + th / 2), L("06c")], "Rechazada",
                   at=(x6, rows(1) + 60), **sys_))
    xb = cols(3) + tw_ / 2
    a.append(arrow([B("03"), (xb, rows(1) + th / 2), L("05")], "Mantener STOP", at=(xb, rows(1) + 40)))
    a.append(arrow([(xb, rows(1) + th / 2), (xb, rows(2) + th / 2), L("09")], "Batería < 25 %",
                   at=(xb + 60, rows(2) + th / 2), **sys_))
    a.append(arrow([(xb, rows(2) + th / 2), (xb, rows(3) + th / 2), L("10")], "Sin datos > 3 s",
                   at=(xb + 60, rows(3) + th / 2), **sys_))
    a.append(arrow([(xb, rows(3) + th / 2), (xb, rows(4) + th / 2), L("12")], "Obstáculo",
                   at=(xb + 60, rows(4) + th / 2), **sys_))
    a.append(arrow([(xb, rows(4) + th / 2), (xb, rows(5) + th / 2), L("14")], "Persona",
                   at=(xb + 60, rows(5) + th / 2), **sys_))
    a.append(arrow([R("09", 0.3), (cols(4) + tw_ + 40, rows(2) + th * 0.3),
                    (cols(4) + tw_ + 40, rows(0) + th * 0.7), R("04", 0.7)], "Volver a\nla base",
                   at=(cols(4) + tw_ + 40, rows(1) + th / 2)))
    a.append(arrow([R("10"), L("06d")], "Orden\n5 s sin ack", **sys_))
    a.append(arrow([R("06d"), L("11")], "Vuelve el\nenlace", **sys_))
    a.append(arrow([R("12"), L("13")], "Decidir"))
    x3 = cols(3) + tw_ / 2 + 22
    xr, yr = cols(6) + tw_ + 70, rows(1) - 36
    a.append(arrow([T("13", 0.8), (cols(5) + tw_ * 0.8, rows(4) - 40), (xr, rows(4) - 40),
                    (xr, yr), (x3, yr), (x3, rows(0) + th + 58)],
                   "Enviar decisión: vuelve a Supervisión", at=(cols(5) + 20, yr)))
    a.append(arrow([L("15"), R("14")], "Decidir"))
    a.append(arrow([B("16"), T("17")], "Tocar\nuna ronda"))
    out += a
    # barra inferior y panel: se llega desde cualquier pestaña
    out.append(g([rect(cols(0) - 10, rows(4) - 10, 360, 120, 16, "none", C["line"], 1.5),
                  text(cols(0) + 8, rows(4) + 20, "Desde cualquier pestaña", 14, 700, C["ink"]),
                  text(cols(0) + 8, rows(4) + 44,
                       "Barra inferior: Supervisión (02, 03), Misiones (07),\nAlertas (15) e Historial (16).\n"
                       "Ícono de herramienta: Panel de simulación (18).", 13, 400, C["muted"], lh=20)], "Nota/Barra inferior"))
    out.append(arrow([R("16", 0.3), L("18", 0.3)], "Herramienta", **sys_))
    return svg_doc(1900, rows(5) + th + 110, "".join(out), BOARD_BG, "Mapa de navegación")


# ------------------------------------------------------------ flujos -------
def flow_row(y, steps, labels, notes, s=0.42, x0=60, gap=150, prefix="f"):
    """Fila de un flujo: frames grandes en secuencia, la acción en cada flecha y una nota por paso."""
    tw_, th = W * s, H * s
    out = []
    for i, sid in enumerate(steps):
        x = x0 + i * (tw_ + gap)
        out.append(thumb(sid, x, y, s, prefix=prefix))
        out.append(g([circle(x + 14, y - 22, 13, C["blue"]), text(x + 14, y - 17, str(i + 1), 13, 700, C["navy"], SANS, "middle")],
                     f"Paso {i + 1}"))
        note = "\n".join(wrap(notes[i], 13, tw_ + gap - 30))
        out.append(text(x, y + th + 66, note, 13, 400, C["muted"], lh=19))
        if i < len(steps) - 1:
            lab = labels[i]
            dash = dict(color=C["muted"], dash="6 5") if lab.startswith("~") else {}
            out.append(arrow([(x + tw_ + 8, y + th / 2), (x + tw_ + gap - 8, y + th / 2)], lab.lstrip("~"), **dash))
    return out


def board_flow_normal():
    out = board_head("Flujo normal · ronda completa",
                     "Escenario 1: el guardia elige el robot, inicia la Ronda completa, la sigue en el plano y "
                     "después la revisa en el Historial.")
    out += flow_row(200, ["01", "02", "07", "03", "16", "17"],
                    ["Vigía-01", "Elegir ronda", "Iniciar", "~Termina\nla ronda", "Tocar\nla ronda"],
                    ["Vigía-02 está fuera de servicio: se ve atenuado y no se puede tocar.",
                     "Robot en base y cargando. Solo Elegir ronda está habilitada; las otras dicen por qué no.",
                     "Las tres rondas con puntos, metros y duración estimada. Iniciar habilitado con batería ≥ 30 %.",
                     "Con el inicio confirmado vuelve a Supervisión: ruta en el plano, puntos verificados en verde.",
                     "El robot vuelve solo a la base. La ronda queda guardada con su resultado.",
                     "La línea de tiempo muestra cada punto verificado, alertas y decisiones."])
    return svg_doc(2050, 700, "".join(out), BOARD_BG, "Flujo normal")


def board_flow_failure():
    out = board_head("Flujo con falla · desconexión y obstáculo",
                     "Escenarios 3 y 4. Las flechas punteadas son cambios que vienen del robot o de la conexión, "
                     "no de un toque del guardia.")
    out.append(section_label(60, 190, "A · Desconexión (escenario 3)"))
    out += flow_row(240, ["03", "10", "06d", "11"],
                    ["~Sin datos\n> 3 s", "Pausar", "~Vuelve el\nenlace"],
                    ["Ronda en curso con datos al día.",
                     "Banner rojo, plano y tarjetas en gris, \"hace 12 s\". Las órdenes siguen visibles pero avisan "
                     "que pueden no llegar.",
                     "La orden queda Sin respuesta a los 5 s. Si el corte pasa de 60 s el robot vuelve solo a la base.",
                     "Al reconectar todo se actualiza. El chip Sin respuesta queda a la vista para que el guardia "
                     "sepa que la pausa no se aplicó."],
                    prefix="fa")
    out.append(section_label(60, 840, "B · Obstáculo en el pasillo B (escenario 4)"))
    out += flow_row(890, ["03", "12", "13", "17"],
                    ["~Obstáculo", "Decidir", "Enviar\ndecisión"],
                    ["El robot patrulla hacia el Pasillo de aulas B.",
                     "Estado Incidente en rojo y banner que pide una decisión. Pausar se deshabilita y dice por qué.",
                     "Se elige primero y se envía aparte, para evitar un toque accidental.",
                     "La ronda termina con 7/8 puntos. Alerta, decisión y orden quedan en el Historial."],
                    prefix="fb")
    return svg_doc(1500, 1360, "".join(out), BOARD_BG, "Flujo con falla")


# --------------------------------------------------------- sistema de diseño
def not_applicable(msg):
    return lambda x, y: para(x, y, msg, "bodyMedium", C["grey"], 340, italic=True)


def slots_cell(slots):
    def fn(x, y):
        parts = [command_slot(x + i * 120, y, 112, s) for i, s in enumerate(slots)]
        return g([p for p, _ in parts], "Órdenes"), max(h for _, h in parts)
    return fn


def chips_cell(chips):
    def fn(x, y):
        out, cy = [], y
        for label, tone in chips:
            chip, _ = status_chip(x, cy, label, tone)
            out.append(chip)
            cy += 30
        return g(out, "Chips"), cy - 8 - y
    return fn


def tcard_cell(**c):
    def fn(x, y):
        h = telemetry_card_h(170, **c)
        return telemetry_card(x, y, 170, h, **c), h
    return fn


def stop_cell(**kw):
    return lambda x, y: hold_to_stop(x, y, 340, **kw)


def notice_cell(msg, color, bg=None, bg_opacity=0.12, action=None):
    actions = [btn_item(action, color=color)] if action else None
    return lambda x, y: notice(x, y, 340, msg, color, bg, bg_opacity, actions)


def dialog_cell(scale, *args, **kw):
    def fn(x, y):
        box, h = dialog_box(*args, x=0, **kw)
        return g(box, transform=f"translate({x} {y}) scale({scale})"), h * scale
    return fn


def robot_cell(name, note, available):
    return lambda x, y: robot_card(x, y, 340, name, note, available)


def board_system():
    out = board_head("Sistema de diseño · Vigía",
                     "Tema oscuro de la app (ui/theme/Theme.kt): el guardia trabaja de noche en una central con poca "
                     "luz. El color siempre va acompañado de texto.")
    # colores
    out.append(section_label(60, 190, "Color"))
    swatches = [("navy", "Navy", "Fondo"), ("surface", "NavySurface", "Barras y hojas"),
                ("raised", "NavyRaised", "Tarjetas y plano"), ("line", "Line", "Bordes"), ("ink", "Ink", "Texto"),
                ("muted", "Muted", "Texto secundario"), ("blue", "Blue", "Primario, ruta"),
                ("blueDeep", "BlueDeep", "Tonal, pestaña activa"), ("green", "ok", "Normal"),
                ("amber", "warning", "Advertencia"), ("red", "error", "Error, STOP"), ("grey", "stale", "Datos antiguos")]
    for i, (k, name, use) in enumerate(swatches):
        x = 60 + i * 148
        out += [rect(x, 210, 128, 72, 12, C[k], C["line"]), text(x, 306, name, 14, 600, C["ink"]),
                text(x, 324, C[k].upper(), 12, 500, C["muted"], MONO), text(x, 342, use, 12, 400, C["muted"])]
    out.append(text(60 + 8 * 148, 372, "StatusColors: el mismo significado en todas las pantallas", 12, 500, C["muted"]))
    # tipografía
    out.append(section_label(60, 430, "Tipografía"))
    ty = [("headlineSmall · Inter 24", "headlineSmall", "Selección de robot"),
          ("titleLarge · Inter 22", "titleLarge", "Patrullando"),
          ("titleMedium · Inter 16 / 500", "titleMedium", "Rondas disponibles"),
          ("titleSmall · Inter 14 / 500", "titleSmall", "Mantené apretado 1,5 s para detener"),
          ("bodyMedium · Inter 14", "bodyMedium", "El robot interrumpe lo que está haciendo y regresa a la base de carga."),
          ("bodySmall · Inter 12", "bodySmall", "Detención simulada por software. No reemplaza la parada de emergencia física."),
          ("labelMedium · Inter 12 / 500", "labelMedium", "Última actualización")]
    y = 460
    for label, style, sample in ty:
        size, lh, _ = T[style]
        out += [text(60, y + lh / 2 + 5, label, 13, 500, C["muted"]), para(330, y, sample, style, C["ink"], 620)[0]]
        y += lh + 14
    out += [text(60, y + 19, "Números · JetBrains Mono 20 / 500", 13, 500, C["muted"]),
            text(330, y + 21, "64 %   0,8 m/s   3/8   02:45:10", 20, 500, C["ink"], MONO)]
    # plano
    out.append(section_label(1100, 430, "Plano (Facility.kt, 40 × 20 m)"))
    m, mh = facility_map("ds-map", 1100, 450, 400, "ronda-completa", {"CP1", "CP2", "CP3"}, "CP4", (24.0, 5.0, 350))
    out.append(m)
    ly = 450 + mh + 30
    legend = [(rect(1100, ly - 10, 10, 10, 0, C["blue"]), "Base de carga"),
              (circle(1105, ly - 5, 5, C["green"]), "Punto verificado"),
              (circle(1105, ly - 5, 5, "none", C["ink"], 1.5), "Punto pendiente de la ruta"),
              (circle(1105, ly - 5, 7, "none", C["blue"], 1.5), "Punto al que va el robot"),
              (circle(1105, ly - 5, 6, C["blue"]), "Robot y rumbo"),
              (rect(1099, ly - 11, 12, 12, 0, "none", C["red"], 1.5, dash="3 2"), "Zona restringida")]
    for i, (mark, label) in enumerate(legend):
        lx, dy = (0 if i < 3 else 230), (i % 3) * 26
        out.append(g(mark, transform=f"translate({lx} {dy})"))
        out.append(text(1100 + lx + 20, ly + dy, label, 13, 400, C["muted"]))
    out.append(text(1100, ly + 82, "Sin datos recientes todo el plano pasa a gris (frame 10).", 13, 400, C["muted"]))

    # componentes x estados
    top = 920
    out.append(section_label(60, top, "Componentes y estados"))
    cols_x = [340, 740, 1140, 1540]
    heads = ["Normal", "Deshabilitado o datos antiguos", "Advertencia", "Error"]
    head_col = [C["green"], C["grey"], C["amber"], C["red"]]
    for x, h_, col in zip(cols_x, heads, head_col):
        out += [rect(x, top + 24, 360, 4, 2, col), text(x, top + 50, h_, 15, 700, col)]
    rows = [
        ("Botón de orden", "CommandSlot: motivo si está deshabilitado, aviso si no hay datos y estado de la última orden.", [
            slots_cell([slot("Pausar"), slot("Cancelar", "danger"), slot("Volver a la base", "tonal")]),
            slots_cell([slot("Pausar", enabled=False, reason="Solo se puede pausar durante la patrulla"),
                        slot("Cancelar", "danger", False, "No hay una misión en curso"),
                        slot("Volver a la base", "tonal", False, "El robot ya está en la base")]),
            slots_cell(stale_slots(PATROL["slots"])),
            slots_cell([slot("Pausar"), slot("Cancelar", "danger"),
                        slot("Volver a la base", "tonal", status=("Rechazada: Camino bloqueado", "error"))]),
        ]),
        ("Chip de estado", "StatusChip: estado de una orden o de la conexión. Pendiente es neutro.", [
            chips_cell([("Conectado", "ok"), ("Pendiente", "neutral"), ("Confirmada", "ok")]),
            chips_cell([("Sin datos", "stale"), ("No disponible", "stale")]),
            chips_cell([("Señal débil", "warning"), ("Sin respuesta", "warning"), ("Advertencia", "warning")]),
            chips_cell([("Rechazada: Camino bloqueado", "error"), ("Sin datos", "error"), ("Crítica", "error")]),
        ]),
        ("Tarjeta de telemetría", "TelemetryCard: el valor toma el color del estado; con datos viejos, todo gris.", [
            tcard_cell(title="Batería", value="64 %", detail="72 min restantes", tone="ok", mono=True),
            tcard_cell(title="Batería", value="64 %", detail="72 min restantes", tone="stale", mono=True),
            tcard_cell(title="Batería", value="22 %", detail="26 min restantes", tone="warning", mono=True),
            tcard_cell(title="Estado", value="Incidente", tone="error"),
        ]),
        ("Detención mantenida", "HoldToStopButton: 1,5 s apretado; el anillo muestra el avance.", [
            stop_cell(progress=0.62, touch=True),
            not_applicable("No se deshabilita nunca: la detención siempre se puede mandar."),
            stop_cell(warning=STALE_WARNING, status=("Sin respuesta", "warning")),
            not_applicable("No tiene rechazo: como mucho queda sin respuesta."),
        ]),
        ("Aviso y banner", "Notice: el color marca la gravedad y el texto la explica.", [
            not_applicable("Sin aviso: solo aparece cuando hace falta actuar."),
            notice_cell("Sin datos hace 12 s. Las órdenes pueden no llegar", C["red"], C["grey"], 0.2),
            notice_cell("Batería baja: conviene volver a la base.", C["amber"], action="Volver a la base"),
            notice_cell("Incidente: requiere tu decisión", C["red"], action="Decidir"),
        ]),
        ("Tarjeta de robot", "RobotItem: el no disponible queda atenuado, sin acción y con el motivo.", [
            robot_cell("Vigía-01", "Simulado", True),
            robot_cell("Vigía-02", "Fuera de servicio: batería en mantenimiento", False),
            not_applicable("No aplica."),
            not_applicable("No aplica."),
        ]),
        ("Diálogo de decisión", "IncidentDecisionDialog: se elige primero y se envía aparte.", [
            dialog_cell(0.9, "Obstáculo en el camino", [p_item("Obstáculo bloquea el paso · Pasillo B"),
                                                         decision_options(OBSTACLE_OPTIONS, "Saltear el punto")],
                        "Enviar decisión", "Más tarde", warn_icon=True),
            dialog_cell(0.9, "Obstáculo en el camino", [p_item("Obstáculo bloquea el paso · Pasillo B"),
                                                         decision_options(OBSTACLE_OPTIONS)],
                        "Enviar decisión", "Más tarde", warn_icon=True, confirm_enabled=False),
            not_applicable("Si la decisión queda sin respuesta, el chip ámbar aparece debajo de las opciones."),
            not_applicable("Si el robot la rechaza, el chip rojo dice el motivo."),
        ]),
    ]
    y = top + 80
    for name, desc, cells in rows:
        parts = [text(60, y + 18, name, 16, 700, C["ink"]),
                 text(60, y + 42, "\n".join(wrap(desc, 13, 250)), 13, 400, C["muted"], lh=19)]
        hmax = 0
        for x, cell in zip(cols_x, cells):
            svg, h = cell(x, y)
            parts.append(svg)
            hmax = max(hmax, h)
        hmax = max(hmax, 42 + 19 * len(wrap(desc, 13, 250)))
        out.append(g(parts, "Fila/" + name))
        y += hmax + 28
        out.append(line(60, y - 14, 1900, y - 14, C["line"], 1, opacity=0.6))
    # forma y espacio
    out.append(section_label(60, y + 30, "Forma y espacio"))
    spec = [("Grilla", "Márgenes de 16 dp, separación de 12 dp entre bloques y 8 dp entre tarjetas."),
            ("Radios", "Tarjetas y plano 12 dp, diálogos y hojas 28 dp, botones y chips en píldora."),
            ("Objetivos táctiles", "Botones de 40 dp de alto; STOP de 72 dp, separado y en rojo."),
            ("Órdenes peligrosas", "Cancelar y Volver a la base piden confirmación; detener se mantiene 1,5 s.")]
    for i, (a_, b_) in enumerate(spec):
        x = 60 + i * 470
        out += [text(x, y + 66, a_, 15, 700, C["ink"]), text(x, y + 90, "\n".join(wrap(b_, 13, 430)), 13, 400, C["muted"], lh=19)]
    return svg_doc(1960, y + 150, "".join(out), BOARD_BG, "Sistema de diseño")


BOARDS = [  # (slug, título, fn)
    ("00-sistema-de-diseno", "Sistema de diseño", board_system),
    ("00-mapa-de-navegacion", "Mapa de navegación", board_navigation),
    ("00-flujo-normal", "Flujo normal", board_flow_normal),
    ("00-flujo-con-falla", "Flujo con falla", board_flow_failure),
]


# ================================================================ GALERÍA ====
# Estado que muestra cada frame y la decisión de diseño detrás (se usa en index.html)
NOTES = {
    "01": ("Normal y deshabilitado", "Vigía-02 se ve atenuado y con el motivo escrito. No se puede tocar, así el guardia "
                                     "no elige un robot que no va a responder."),
    "02": ("Normal", "Robot en base y cargando. Solo Elegir ronda está habilitada; Cancelar y Volver a la base dicen "
                     "por qué no se pueden usar."),
    "03": ("Normal", "Plano con la ruta y los puntos verificados en verde, tarjetas de telemetría y la barra de órdenes "
                     "siempre abajo. La detención queda separada y en rojo."),
    "04": ("Confirmación", "Volver a la base corta la ronda, por eso pide confirmación. Descartar queda a la izquierda, "
                           "lejos de la acción."),
    "05": ("En progreso", "La detención se mantiene apretada 1,5 s. El anillo muestra cuánto falta y al lado se aclara "
                          "que es una detención simulada por software."),
    "06a": ("Pendiente", "La orden queda Pendiente y el botón se deshabilita para no mandarla dos veces."),
    "06b": ("Confirmada", "Confirmada quiere decir que el robot aceptó la orden. El estado pasa a Regresando y las otras "
                          "órdenes explican por qué ya no aplican."),
    "06c": ("Error", "El robot rechazó la orden y el chip dice el motivo. El botón sigue habilitado para volver a intentar."),
    "06d": ("Advertencia", "Sin acuse a los 5 s la orden queda Sin respuesta, en ámbar. Pasa durante una desconexión."),
    "07": ("Normal", "Las tres rondas con puntos, metros, duración estimada y recorrido. Iniciar está habilitado porque el "
                     "robot está en base con batería."),
    "08": ("Deshabilitado", "Con menos de 30 % de batería Iniciar se deshabilita en las tres rondas. El motivo aparece "
                            "una sola vez arriba."),
    "09": ("Advertencia", "Debajo del 25 % aparece el aviso ámbar con Volver a la base a mano, sin buscarlo en la barra."),
    "10": ("Error y datos antiguos", "Más de 3 s sin datos: banner rojo, plano y tarjetas en gris y la antigüedad a la "
                                     "vista. Las órdenes siguen visibles pero avisan que pueden no llegar."),
    "11": ("Normal", "Al volver el enlace todo se actualiza. El chip Sin respuesta de la pausa queda a la vista para que "
                     "el guardia sepa que no se aplicó."),
    "12": ("Error", "El robot está detenido esperando. El banner rojo lleva directo al diálogo y Pausar explica por qué "
                    "no aplica."),
    "13": ("Decisión", "Cada opción dice qué va a hacer el robot. Se elige primero y se envía aparte para evitar un toque "
                       "accidental."),
    "14": ("Decisión, envío deshabilitado", "Muestra lo que dijo el robot por el parlante y la nota de privacidad. Enviar "
                                            "decisión queda deshabilitado hasta elegir una opción."),
    "15": ("Normal", "Las activas van arriba, con el borde y el chip del color de la severidad. Las reconocidas y "
                     "resueltas bajan y se atenúan."),
    "16": ("Normal", "Rondas guardadas con resultado, puntos verificados, personas y puertas. Borrar historial está en "
                     "rojo y pide confirmación."),
    "17": ("Normal", "Al tocar una ronda se abre su línea de tiempo: puntos, alertas, decisiones y órdenes con su hora."),
    "18": ("Solo demo", "Hoja inferior para forzar los cinco escenarios en la presentación. El guardia no la vería en la "
                        "app real."),
}


def build_index(path):
    """Galería autocontenida: los frames van como SVG en línea y los tableros como PNG con link al SVG."""
    sections = {}
    for e in SCREENS:
        sections.setdefault(e[3], []).append(e)
    parts = []
    for slug, title, _ in BOARDS:
        parts.append(f'<h2>{esc(title)}</h2><a class="board" href="boards/{slug}.svg">'
                     f'<img src="png/{slug}.png" alt="{esc(title)}" loading="lazy"></a>')
    for sec in sorted(sections):
        parts.append(f'<h2>{esc(sec)}</h2><div class="grid">')
        for sid, slug, name, _, fn in sections[sec]:
            state, why = NOTES[sid]
            svg = (f'<svg viewBox="0 0 {W} {H}" role="img" aria-label="{esc(name)}">'
                   f'<rect width="{W}" height="{H}" fill="{C["navy"]}"/>' + "".join(fn(f"g{sid}")) + "</svg>")
            parts.append(f'<figure id="f{sid}"><a class="phone" href="screens/{sid}-{slug}.svg">{svg}</a><figcaption>'
                         f'<span class="id">{sid}</span><strong>{esc(name)}</strong>'
                         f'<span class="state">{esc(state)}</span><p>{esc(why)}</p></figcaption></figure>')
        parts.append("</div>")
    css = (":root{--bg:%(navy)s;--card:%(surface)s;--ink:%(ink)s;--muted:%(muted)s;--line:%(line)s;"
           "--blue:%(blue)s;--deep:%(blueDeep)s}" % C) + """
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.5 Inter,sans-serif}
header{background:var(--card);border-bottom:1px solid var(--line);padding:40px 24px}
header div,main{max-width:1320px;margin:0 auto}h1{font:700 clamp(28px,4vw,44px)/1.1 Inter,sans-serif;margin:0 0 12px}
header p{max-width:780px;color:var(--muted);margin:0}main{padding:8px 16px 80px}
h2{font:700 22px Inter,sans-serif;margin:48px 0 16px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:28px}
figure{margin:0}.phone{display:block;border-radius:24px;overflow:hidden;border:1px solid var(--line)}
.phone svg{display:block;width:100%;height:auto}figcaption{padding:12px 4px 0}figcaption strong{display:block}
.id{font:600 12px 'JetBrains Mono',monospace;color:var(--blue)}
.state{display:inline-block;margin:6px 0;padding:2px 10px;border-radius:99px;background:var(--deep);font-size:12px;font-weight:600}
figcaption p{margin:0;color:var(--muted);font-size:14px}
.board{display:block;overflow-x:auto;border:1px solid var(--line);border-radius:16px}
.board img{display:block;width:100%;min-width:1000px;height:auto}
"""
    page = ('<!doctype html><html lang="es"><head><meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width,initial-scale=1"><title>Vigía · Frames para Figma</title>'
            f'<link rel="stylesheet" href="{esc(FONTS)}"><style>{css}</style></head><body>'
            '<header><div><h1>Vigía · frames para Figma</h1>'
            f'<p>{len(SCREENS)} frames Android de 360 × 800 dp y {len(BOARDS)} tableros, generados con build.py a partir '
            'del tema y las pantallas de la app. Cada frame dice qué estado muestra y por qué está resuelto así. '
            'Tocá un frame o un tablero para abrir el SVG.</p></div></header>'
            f'<main>{"".join(parts)}</main></body></html>')
    with open(path, "w", encoding="utf-8") as f:
        f.write(page)


# ================================================================ SALIDA =====


def svg_doc(w, h, body, bg, title):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">'
            f'<title>{esc(title)}</title><style>@import url(\'{esc(FONTS)}\');</style>'
            f'<rect width="{w}" height="{h}" fill="{bg}"/>{body}</svg>')


def render_screen(entry):
    sid, slug, name, section, fn = entry
    return svg_doc(W, H, "".join(fn(f"s{sid}")), C["navy"], f"{sid} · {name}")


def export_png(files, pdir):
    """Captura cada SVG con Edge headless a 2x.
    Edge puede volver antes de escribir el archivo: se usa un perfil nuevo por captura y se espera el PNG."""
    edge = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
    os.makedirs(pdir, exist_ok=True)
    missing, profiles = [], []
    for p, w, h in files:
        out = os.path.join(pdir, os.path.basename(p)[:-4] + ".png")
        if os.path.exists(out):
            os.remove(out)
        profile = tempfile.mkdtemp(prefix="vigia-edge-")
        profiles.append(profile)
        subprocess.run([edge, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                        "--force-device-scale-factor=2", f"--window-size={w},{h}",
                        "--virtual-time-budget=4000", f"--user-data-dir={profile}", f"--screenshot={out}",
                        "file:///" + p.replace("\\", "/") + f"?v={time.time_ns()}"],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        deadline = time.time() + 30
        while not os.path.exists(out) and time.time() < deadline:
            time.sleep(0.25)
        if not os.path.exists(out):
            missing.append(os.path.basename(out))
    for d in profiles:
        shutil.rmtree(d, ignore_errors=True)
    print(f"{len(files) - len(missing)} png exportados en {pdir}")
    if missing:
        sys.exit("No se pudieron exportar: " + ", ".join(missing))


def main(png=False):
    sdir, bdir = os.path.join(ROOT, "screens"), os.path.join(ROOT, "boards")
    for d in (sdir, bdir):
        os.makedirs(d, exist_ok=True)
    SCREENS.sort(key=lambda e: e[0])
    files = []
    for e in SCREENS:
        p = os.path.join(sdir, f"{e[0]}-{e[1]}.svg")
        with open(p, "w", encoding="utf-8") as f:
            f.write(render_screen(e))
        files.append((p, W, H))
    for slug, title, fn in BOARDS:
        p = os.path.join(bdir, f"{slug}.svg")
        svg = fn()
        with open(p, "w", encoding="utf-8") as f:
            f.write(svg)
        bw, bh = (int(float(v)) for v in svg.split('viewBox="0 0 ')[1].split('"')[0].split())
        files.append((p, bw, bh))
    build_index(os.path.join(ROOT, "index.html"))
    print(f"{len(SCREENS)} pantallas, {len(BOARDS)} tableros e index.html generados")
    if png:
        export_png(files, os.path.join(ROOT, "png"))


if __name__ == "__main__":
    main(png="--png" in sys.argv)
