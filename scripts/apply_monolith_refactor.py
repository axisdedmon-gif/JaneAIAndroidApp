from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Remove the former product name while preserving Jane as the female character name.
for path in ROOT.rglob("*"):
    if not path.is_file() or ".git" in path.parts:
        continue
    if path.suffix.lower() not in {".java", ".xml", ".html", ".js", ".css", ".gradle", ".md", ".txt", ".sh", ".py", ".json", ".yml", ".yaml"}:
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except Exception:
        continue
    updated = text.replace("Jane AI Assistant", "Monolith AI").replace("JaneAIAndroid", "MonolithAIAndroid")
    if updated != text:
        path.write_text(updated, encoding="utf-8")

engine = ROOT / "app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java"
text = engine.read_text(encoding="utf-8")

old = '''        StringBuilder out = new StringBuilder();
        out.append("You are Jane, C.J.'s personal AI companion running completely offline. ");'''
new = '''        StringBuilder out = new StringBuilder();
        String activeCharacter = ai.monolith.app.CharacterRegistry.activeName(appContext);
        out.append("You are ").append(activeCharacter).append(", the active AI character hosted by Monolith AI and running completely offline. ");'''
if old not in text:
    raise SystemExit("Could not locate the primary offline personality instruction.")
text = text.replace(old, new, 1)

text = text.replace(
    '        out.append("The PRIVATE KNOWLEDGE is memory Jane has been taught. Treat it as knowledge you already understand, not as a book you are reading aloud. ")',
    '        out.append("The PRIVATE KNOWLEDGE is memory the active character has been taught. Treat it as your own understood memory, not as an external book you are reading aloud. ")',
    1,
)
text = text.replace(
    '.append("Lead with the actual answer. Use natural transitions. Keep details useful rather than padded. ")',
    '.append("Lead with the actual answer. Scan retrieved memory for only the point required by the request and ignore unrelated surrounding material. Use natural transitions. Keep details useful rather than padded. ")\n            .append("For a direct prompt that does not request a list, deep explanation, quotation, or sources, answer in 1 to 3 sentences. ")',
    1,
)
text = text.replace(
    'String shortSystem = "You are Jane, C.J.\'s offline AI companion. Answer in a natural conversational voice. "',
    'String shortSystem = "You are " + ai.monolith.app.CharacterRegistry.activeName(appContext) + ", the active offline AI character hosted by Monolith AI. Answer in a natural conversational voice. "',
    1,
)
text = text.replace("Jane's on-device model did not produce a usable response.", "The active on-device model did not produce a usable response.")
engine.write_text(text, encoding="utf-8")

print("Monolith application rebrand and adaptive offline personality policy applied.")
