from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
INDEX = ROOT / "app/src/main/assets/index.html"

html = INDEX.read_text(encoding="utf-8")

# The BIOS is the only pre-launch boundary. Remove the complete historical body-level owner gate;
# the command deck no longer needs a hidden compatibility anchor.
gate_pattern = re.compile(
    r'''(?s)<div\s+id=["']ownerGate["'][^>]*>.*?</div>\s*</div>\s*(?=<div\s+class=["']screen["'])'''
)
html, gate_count = gate_pattern.subn("", html, count=1)

# Remove the gate-only style blocks and startup handlers as well. Leaving these dormant makes a
# later DOM mutation capable of restoring the page and keeps a load listener on the critical path.
gate_css_pattern = re.compile(
    r'''(?s)\n*/\* V16: House Dedmon access check splash gate \*/.*?(?=/\* V18: restore original visual-novel dialog composition\.)'''
)
html, gate_css_count = gate_css_pattern.subn("\n", html, count=1)

launch_button_css_pattern = re.compile(
    r'''(?s)\n\s*\.launchJaneButton\{.*?#ownerGate #ownerGateStatus\.launchOnly,.*?\n\s*\}\s*(?=/\* V35: dialog text box scroll fix only \*/)'''
)
html, launch_css_count = launch_button_css_pattern.subn("\n    ", html, count=1)

startup_script_pattern = re.compile(
    r'''(?s)\nconst STARTUP_LINES=\[.*?\n\nfunction decodeBase64Utf8\(base64\)\{'''
)
html, startup_script_count = startup_script_pattern.subn("\nfunction decodeBase64Utf8(base64){", html, count=1)
html = html.replace("\nwindow.addEventListener('load', speakStartupLine);", "")
html = html.replace(
    '["ownerGate","ownerModal","travelModal","janeMeshyStudio","janeMusicStudio"]',
    '["ownerModal","travelModal","janeMeshyStudio","janeMusicStudio"]',
)

v73_pattern = re.compile(
    r'''(?s)\n<style id="v73-hide-vitals-on-launch-only-css">.*?<!-- V73: launch screen logic restored from V70; only vitals visibility is gated while ownerGate is visible\. -->\n'''
)
html, v73_count = v73_pattern.subn("\n", html, count=1)

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
    'id="ownerGate"',
    'ownerGate',
    'launchJaneButton',
    'speakStartupLine',
    'monolith-launch',
):
    if forbidden in html:
        raise SystemExit(f"Deprecated index-level visual payload survived cleanup: {forbidden}")

INDEX.write_text(html, encoding="utf-8")
print(
    "Scene cleanup applied: "
    f"legacy House Dedmon overlay removed={gate_count}; "
    f"gate CSS removed={gate_css_count + launch_css_count}; "
    f"gate startup handlers removed={startup_script_count}; "
    f"gate vitals rule removed={v73_count}; "
    f"fake biometric payload blocks removed={vitals_count}."
)
