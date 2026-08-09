from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
INDEX = ROOT / "app/src/main/assets/index.html"

html = INDEX.read_text(encoding="utf-8")

# The native Monolith BIOS is the only launch scene. The old House Dedmon gate remains only as
# a hidden compatibility anchor because the preserved command-deck initializer still looks up
# #ownerGate and specifically checks its legacy `hidden` CSS class before MonolithSceneRuntime
# takes exclusive ownership of navigation.
compat_gate = (
    '<div id="ownerGate" class="hidden" hidden aria-hidden="true" style="display:none!important">'
    '<button id="launchJaneButton" type="button" hidden tabindex="-1" aria-hidden="true"></button>'
    '</div>'
)

# Match the complete legacy gate by using the following .screen container as the structural
# boundary. This deliberately accepts both historical owner-card and owner-gate-card revisions.
gate_pattern = re.compile(
    r'''(?s)<div\s+id=["']ownerGate["'][^>]*>.*?</div>\s*</div>\s*(?=<div\s+class=["']screen["'])'''
)
html, gate_count = gate_pattern.subn(compat_gate + "\n\n", html, count=1)

if gate_count == 0:
    # Idempotent builds are valid when the compatibility anchor is already present.
    if 'id="ownerGate"' not in html:
        raise SystemExit("Owner-gate compatibility anchor is missing.")
    if "House Dedmon Access" in html or 'class="owner-gate"' in html or 'class="owner-card"' in html:
        raise SystemExit("Legacy House Dedmon gate changed shape; refusing to package an uncertain overlay.")
    if 'id="ownerGate" class="hidden"' not in html:
        raise SystemExit("Owner-gate compatibility anchor is not marked with the legacy hidden class.")

# Remove former simulated human-biometric card payloads. Real telemetry comes from AndroidHud.
# Never allow invented heart rate, body temperature, oxygen, blood pressure, hydration, neural
# state, energy, or sync values to present themselves as live device data.
vitals_pattern = re.compile(
    r'''(?s)  const cards = \[\s*\n\s*\["left",\s*"❤️",\s*"Heart",\s*"72 BPM".*?\n\s*\];'''
)
html, vitals_count = vitals_pattern.subn("  const cards = [];", html)

for forbidden in (
    '"72 BPM"',
    '"98.6°F"',
    'House Dedmon Access',
):
    if forbidden in html:
        raise SystemExit(f"Deprecated visual payload survived cleanup: {forbidden}")

INDEX.write_text(html, encoding="utf-8")
print(
    "Scene cleanup applied: "
    f"legacy launch overlays replaced={gate_count}; "
    f"fake biometric payload blocks removed={vitals_count}."
)
