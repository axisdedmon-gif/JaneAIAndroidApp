from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
INDEX = ROOT / "app/src/main/assets/index.html"

html = INDEX.read_text(encoding="utf-8")

# The native Monolith bootstrap is now the only launch scene. Keep a non-visual compatibility
# node so the preserved command-deck initializer can complete before monolith_scene_runtime.js
# takes exclusive ownership of scene routing.
compat_gate = '<div id="ownerGate" hidden aria-hidden="true"><button id="ownerGateLaunch" type="button" hidden></button></div>'
gate_pattern = re.compile(
    r'''(?s)<div\s+id="ownerGate"\s+class="owner-gate"[^>]*>\s*<div\s+class="owner-gate-card"[^>]*>.*?<\/div>\s*<\/div>'''
)
html, gate_count = gate_pattern.subn(compat_gate, html, count=1)
if gate_count == 0 and ("House Dedmon Access" in html or 'class="owner-gate"' in html):
    raise SystemExit("Legacy House Dedmon gate changed shape; refusing to package an uncertain overlay.")
if gate_count == 0 and 'id="ownerGate"' not in html:
    raise SystemExit("Owner-gate compatibility anchor is missing.")

# Remove the former simulated human-biometric card payload. Real device telemetry is supplied by
# AndroidHud/jane_qol_runtime.js; Monolith must never present invented heart rate, temperature,
# oxygen, blood pressure, hydration, neural state, energy, or sync values as live telemetry.
vitals_pattern = re.compile(
    r'''(?s)  const cards = \[\s*\n\s*\["left",\s*"❤️",\s*"Heart",\s*"72 BPM".*?\n\s*\];'''
)
html, vitals_count = vitals_pattern.subn("  const cards = [];", html)
if vitals_count == 0 and ('"72 BPM"' in html or '"98.6°F"' in html):
    raise SystemExit("Legacy fake biometric payload changed shape; refusing to package fake telemetry.")

INDEX.write_text(html, encoding="utf-8")
print(
    "Scene cleanup applied: "
    f"legacy launch overlays replaced={gate_count}; "
    f"fake biometric payload blocks removed={vitals_count}."
)
