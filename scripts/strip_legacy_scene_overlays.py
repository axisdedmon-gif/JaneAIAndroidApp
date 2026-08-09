from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
INDEX = ROOT / "app/src/main/assets/index.html"

html = INDEX.read_text(encoding="utf-8")

legacy_gate = '''<div id="ownerGate" class="owner-gate" role="dialog" aria-modal="true" aria-labelledby="ownerGateTitle">
  <div class="owner-gate-card">
    <img src="house_dedmon_crest.webp" alt="House Dedmon coat of arms" />
    <h2 id="ownerGateTitle">House Dedmon Access</h2>
    <p>If this is C.J, all is well. If not, I’m filing emotional charges.</p>
    <button type="button" class="owner-launch-button" id="ownerGateLaunch">Launch Jane</button>
  </div>
</div>'''

# The native Monolith bootstrap is now the only launch scene. Keep a non-visual compatibility
# node so the preserved command-deck initializer can complete before monolith_scene_runtime.js
# takes exclusive ownership of scene routing.
compat_gate = '''<div id="ownerGate" hidden aria-hidden="true"><button id="ownerGateLaunch" type="button" hidden></button></div>'''

if legacy_gate in html:
    html = html.replace(legacy_gate, compat_gate, 1)
elif "House Dedmon Access" in html or 'class="owner-gate"' in html:
    raise SystemExit("Legacy House Dedmon gate changed shape; refusing to package an uncertain overlay.")
elif 'id="ownerGate"' not in html:
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
print(f"Scene cleanup applied: native launch boundary retained; fake biometric payload blocks removed={vitals_count}.")
