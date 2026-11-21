#!/usr/bin/env python3
"""
Genera plugin/, un plugin de Figma que importa los frames y arma el prototipo solo.

Los SVG de screens/ y boards/ van embebidos en plugin/code.js, así que el plugin no necesita
leer archivos. Las conexiones son las de la tabla "Armar el prototipo" del README.
Antes de escribir, el script verifica que cada capa nombrada exista en su frame.

    python build_plugin.py      # después de python build.py, si cambiaron los SVG
"""

import json
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SCREENS = ROOT / "screens"
BOARDS = ROOT / "boards"
OUT = ROOT / "plugin"
SVG_NS = "{http://www.w3.org/2000/svg}"

# Barra inferior y botón del panel: están en todos los frames que tienen pestañas
TABS = {
    "Tab/Supervisión": "03",
    "Tab/Misiones": "07",
    "Tab/Alertas": "15",
    "Tab/Historial": "16",
}
PANEL_BUTTON = "Abrir panel de simulación"

# (desde, capa, hacia, disparador). La capa "" es el frame entero; " › " separa capas anidadas.
# Disparadores: tap, press (mientras se mantiene) o un número de segundos (After delay).
CONNECTIONS = [
    ("01", "Robot/Vigía-01", "02", "tap"),
    ("02", "Command slot/Elegir ronda", "07", "tap"),
    ("07", "Route card/Ronda completa › Button/filled/Iniciar", "03", "tap"),
    ("03", "Command slot/Volver a la base", "04", "tap"),
    ("04", "Text button/Volver a la base", "06a", "tap"),
    ("04", "Text button/Descartar", "03", "tap"),
    ("06a", "", "06b", 1),
    ("03", "Hold to stop", "05", "press"),
    ("09", "Notice › Button/filled/Volver a la base", "04", "tap"),
    ("10", "Command slot/Pausar", "06d", "tap"),
    ("06d", "", "11", 3),
    ("12", "Notice › Button/filled/Decidir", "13", "tap"),
    ("13", "Text button/Enviar decisión", "03", "tap"),
    ("13", "Text button/Más tarde", "12", "tap"),
    ("15", "Alert card/Persona en zona restringida › Button/filled/Decidir", "14", "tap"),
    ("16", "Mission card/m-0003", "17", "tap"),
    ("17", "Mission card/m-0003", "16", "tap"),
    ("18", "Control/Conexión con el robot", "10", "tap"),
    ("18", "Control/Obstáculo en el pasillo", "12", "tap"),
    ("18", "Control/Persona en zona restringida", "14", "tap"),
    ("18", "Battery control › Button/outlined/24 %", "09", "tap"),
]

FLOWS = [("Flujo normal", "01"), ("Flujo con falla", "03")]


def frame_key(path: Path) -> str:
    return path.stem.split("-", 1)[0]


def find(root: ET.Element, layer_path: str) -> ET.Element | None:
    node = root
    for name in layer_path.split(" › "):
        node = next((e for e in node.iter(f"{SVG_NS}g") if e.get("id") == name), None)
        if node is None:
            return None
    return node


def main() -> None:
    screens = sorted(SCREENS.glob("*.svg"))
    boards = sorted(BOARDS.glob("*.svg"))
    keys = {frame_key(p): p for p in screens}
    trees = {k: ET.parse(p).getroot() for k, p in keys.items()}

    connections = list(CONNECTIONS)
    for key, root in trees.items():
        if find(root, "Tab/Supervisión") is not None:
            connections += [(key, tab, dest, "tap") for tab, dest in TABS.items()]
        if find(root, PANEL_BUTTON) is not None:
            connections.append((key, PANEL_BUTTON, "18", "tap"))
    # Figma no deja navegar al mismo frame (la pestaña del frame actual, el panel desde el panel)
    connections = [c for c in connections if c[0] != c[2]]

    errors = []
    for src, layer, dest, _ in connections:
        if src not in keys or dest not in keys:
            errors.append(f"frame inexistente: {src} -> {dest}")
        elif layer and find(trees[src], layer) is None:
            errors.append(f"{src}: no está la capa '{layer}'")
    if errors:
        raise SystemExit("\n".join(errors))

    data = {
        "screens": [{"key": frame_key(p), "name": p.stem, "svg": p.read_text(encoding="utf-8")} for p in screens],
        "boards": [{"name": p.stem, "svg": p.read_text(encoding="utf-8")} for p in boards],
        "connections": [
            {"from": s, "layer": l, "to": d, "trigger": t} for s, l, d, t in connections
        ],
        "flows": [{"name": n, "from": k} for n, k in FLOWS],
    }

    OUT.mkdir(exist_ok=True)
    manifest = {
        "name": "Vigía · prototipo",
        "id": "vigia-prototipo-local",
        "api": "1.0.0",
        "main": "code.js",
        "editorType": ["figma"],
        "documentAccess": "dynamic-page",
    }
    (OUT / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    code = "// Generado por build_plugin.py: no editar a mano\nconst DATA = " + json.dumps(data, ensure_ascii=False) + ";\n" + RUNTIME
    (OUT / "code.js").write_text(code, encoding="utf-8")
    print(f"{len(screens)} frames, {len(boards)} tableros, {len(connections)} conexiones -> plugin/")


RUNTIME = r"""
const COLS = 6;
const GAP_X = 80;
const GAP_Y = 160;

function findPath(frame, path) {
  let node = frame;
  for (const name of path.split(" › ")) {
    node = node.findOne((n) => n.name === name);
    if (!node) return null;
  }
  return node;
}

function triggerFor(t) {
  if (t === "tap") return { type: "ON_CLICK" };
  if (t === "press") return { type: "ON_PRESS" };
  return { type: "AFTER_TIMEOUT", timeout: t };
}

async function newPage(name) {
  const page = figma.createPage();
  page.name = name;
  await figma.setCurrentPageAsync(page);
  return page;
}

function place(nodes, cols, gapX, gapY) {
  nodes.forEach((node, i) => {
    node.x = (i % cols) * (node.width + gapX);
    node.y = Math.floor(i / cols) * (node.height + gapY);
  });
}

async function run() {
  const boardsPage = await newPage("Tableros");
  const boards = DATA.boards.map((b) => {
    const node = figma.createNodeFromSvg(b.svg);
    node.name = b.name;
    return node;
  });
  boards.forEach((node, i) => {
    node.x = i === 0 ? 0 : boards[i - 1].x + boards[i - 1].width + 200;
  });

  const page = await newPage("Prototipo");
  const frames = {};
  const ordered = DATA.screens.map((s) => {
    const node = figma.createNodeFromSvg(s.svg);
    node.name = s.name;
    frames[s.key] = node;
    return node;
  });
  place(ordered, COLS, GAP_X, GAP_Y);

  // Varias conexiones pueden salir de la misma capa: se agrupan antes de asignarlas
  const byNode = new Map();
  const missing = [];
  for (const c of DATA.connections) {
    const frame = frames[c.from];
    const node = c.layer ? findPath(frame, c.layer) : frame;
    if (!node) {
      missing.push(c.from + " · " + c.layer);
      continue;
    }
    const reaction = {
      trigger: triggerFor(c.trigger),
      actions: [{
        type: "NODE",
        destinationId: frames[c.to].id,
        navigation: "NAVIGATE",
        transition: null,
        preserveScrollPosition: false,
      }],
    };
    if (!byNode.has(node)) byNode.set(node, []);
    byNode.get(node).push(reaction);
  }
  let count = 0;
  for (const [node, reactions] of byNode) {
    await node.setReactionsAsync(reactions);
    count += reactions.length;
  }

  page.flowStartingPoints = DATA.flows.map((f) => ({ nodeId: frames[f.from].id, name: f.name }));
  figma.viewport.scrollAndZoomIntoView(ordered);

  const note = missing.length ? " Faltaron " + missing.length + " capas: " + missing.join(", ") : "";
  figma.closePlugin(ordered.length + " frames y " + count + " conexiones listas." + note);
}

run().catch((e) => figma.closePlugin("Error: " + (e && e.message ? e.message : e)));
"""

if __name__ == "__main__":
    main()
