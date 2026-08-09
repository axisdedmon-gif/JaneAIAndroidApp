from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BOOTSTRAP = ROOT / "app/src/main/java/ai/monolith/app/MonolithBootstrapActivity.java"

text = BOOTSTRAP.read_text(encoding="utf-8")

clear_anchor = '''            showControls(true);\n        }, ButtonTone.UTILITY);'''
clear_replacement = '''            showControls(true);\n            setCoreBootLocked(false);\n        }, ButtonTone.UTILITY);'''
if clear_anchor in text:
    text = text.replace(clear_anchor, clear_replacement, 1)
elif "setCoreBootLocked(false);" not in text:
    raise SystemExit("Could not harden diagnostic-clear boot unlock path.")

helper_anchor = '''    private void showPersistedDiagnostic(String state) {'''
helper = '''    private void setCoreBootLocked(boolean locked) {\n        if (launchButton == null) return;\n        launchButton.setEnabled(!locked);\n        launchButton.setAlpha(locked ? 0.38f : 1.0f);\n        launchButton.setText(locked ? "CORE BOOT LOCKED // CLEAR DIAGNOSTIC" : "LAUNCH MONOLITH CORE");\n    }\n\n    private void showPersistedDiagnostic(String state) {'''
if helper_anchor in text and "private void setCoreBootLocked(boolean locked)" not in text:
    text = text.replace(helper_anchor, helper, 1)
elif "private void setCoreBootLocked(boolean locked)" not in text:
    raise SystemExit("Could not insert BIOS core-boot lock helper.")

diagnostic_anchor = '''        diagnosticView.setText(report);\n        diagnosticView.setVisibility(View.VISIBLE);\n        showControls(true);\n    }\n\n    private void launchCore() {'''
diagnostic_replacement = '''        diagnosticView.setText(report);\n        diagnosticView.setVisibility(View.VISIBLE);\n        showControls(true);\n        setCoreBootLocked(true);\n    }\n\n    private void launchCore() {'''
if diagnostic_anchor in text:
    text = text.replace(diagnostic_anchor, diagnostic_replacement, 1)
elif "setCoreBootLocked(true);\n    }\n\n    private void launchCore()" not in text:
    raise SystemExit("Could not lock core boot after persisted diagnostic.")

launch_anchor = '''    private void launchCore() {\n        handler.removeCallbacksAndMessages(null);'''
launch_replacement = '''    private void launchCore() {\n        if (launchButton != null && !launchButton.isEnabled()) {\n            setStatus("CORE BOOT LOCKED // CLEAR DIAGNOSTIC FIRST");\n            setBootLog(\n                "> monolith.bootstrap --boot-lock\\n" +\n                "[HOLD] unresolved runtime diagnostic\\n" +\n                "[SAFE] safe base remains available"\n            );\n            return;\n        }\n        handler.removeCallbacksAndMessages(null);'''
if launch_anchor in text:
    text = text.replace(launch_anchor, launch_replacement, 1)
elif "CORE BOOT LOCKED // CLEAR DIAGNOSTIC FIRST" not in text:
    raise SystemExit("Could not add core-launch boot-lock guard.")

# Immediate launch exceptions are structural faults too. They must freeze another core attempt
# until the operator explicitly clears the diagnostic boundary.
for status in ("CORE ACTIVITY NOT FOUND", "CORE LAUNCH FAILED"):
    anchor = f'''            setStatus("{status}");'''
    start = text.find(anchor)
    if start < 0:
        raise SystemExit(f"Could not locate launch fault path: {status}")
    end = text.find("        }", start)
    if end < 0:
        raise SystemExit(f"Could not locate end of launch fault path: {status}")
    block = text[start:end]
    if "setCoreBootLocked(true);" not in block:
        insertion = block.rfind("            showControls(true);")
        if insertion < 0:
            raise SystemExit(f"Could not lock launch fault path: {status}")
        insertion_end = insertion + len("            showControls(true);")
        block = block[:insertion_end] + "\n            setCoreBootLocked(true);" + block[insertion_end:]
        text = text[:start] + block + text[end:]

BOOTSTRAP.write_text(text, encoding="utf-8")
print("Deterministic Startup Boundary hardened: unresolved diagnostics lock core boot while Safe Base and explicit clear/retry remain available.")
