(function () {
  "use strict";

  const VERSION = "MONOLITH-CORE-2";
  if (window.MonolithCore && window.MonolithCore.version === VERSION) {
    window.MonolithCore.refresh();
    return;
  }

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));
  const MODULE_ROUTES = Object.freeze({ model: "monolith-model", voice: "monolith-voice", rpg: "monolith-rpg" });

  const state = {
    activeCharacter: "Jane",
    activeId: "female_jane",
    screen: "",
    voice: null,
    characters: null
  };

  const SF1_SKILLS = [
    ["Acrobatics", "dex"], ["Athletics", "str"], ["Bluff", "cha"], ["Computers", "int"], ["Culture", "int"],
    ["Diplomacy", "cha"], ["Disguise", "cha"], ["Engineering", "int"], ["Intimidate", "cha"], ["Life Science", "int"],
    ["Medicine", "int"], ["Mysticism", "wis"], ["Perception", "wis"], ["Physical Science", "int"], ["Piloting", "dex"],
    ["Profession", "var"], ["Sense Motive", "wis"], ["Sleight of Hand", "dex"], ["Stealth", "dex"], ["Survival", "wis"]
  ];

  function icon(path) {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.25" stroke-linecap="square" stroke-linejoin="miter" aria-hidden="true">${path}</svg>`;
  }

  const I = {
    model: icon('<path d="m12 2 9 5v10l-9 5-9-5V7z"/><path d="m3 7 9 5 9-5M12 12v10"/>'),
    voice: icon('<path d="M4 12v3M8 7v10M12 3v18M16 7v10M20 12v3"/>'),
    rpg: icon('<path d="m12 2 8 6-3 12H7L4 8z"/><path d="m4 8 8 5 8-5M12 13v9"/>'),
    back: icon('<path d="M19 12H5M11 18l-6-6 6-6"/>'),
    user: icon('<circle cx="12" cy="8" r="4"/><path d="M4 22a8 8 0 0 1 16 0"/>'),
    mic: icon('<path d="M9 5a3 3 0 0 1 6 0v7a3 3 0 0 1-6 0z"/><path d="M5 11a7 7 0 0 0 14 0M12 18v4"/>'),
    upload: icon('<path d="M12 16V4M7 9l5-5 5 5"/><path d="M4 20h16"/>'),
    dice: icon('<path d="m12 2 8 6-3 12H7L4 8z"/><circle cx="12" cy="9" r="1"/><circle cx="9" cy="15" r="1"/><circle cx="15" cy="15" r="1"/>'),
    archive: icon('<ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M3 5v14c0 1.7 4 3 9 3s9-1.3 9-3V5M3 12c0 1.7 4 3 9 3s9-1.3 9-3"/>')
  };

  function esc(value) {
    return String(value == null ? "" : value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#39;");
  }

  function call(method, ...args) {
    try {
      if (!window.AndroidMonolith || typeof window.AndroidMonolith[method] !== "function") return null;
      return window.AndroidMonolith[method](...args);
    } catch (error) {
      console.warn("[Monolith]", method, error);
      return null;
    }
  }

  function parse(value, fallback = {}) {
    try {
      return typeof value === "string" ? JSON.parse(value) : (value || fallback);
    } catch (_) {
      return fallback;
    }
  }

  function loadState() {
    const data = parse(call("getSystemState"), {});
    const chars = data.characters || parse(call("getCharacterState"), {});
    state.characters = chars;
    state.activeCharacter = chars.activeName || "Jane";
    state.activeId = chars.activeId || "female_jane";
    state.voice = data.voice || state.voice || parse(call("getVoiceWorkspace"), {});
    document.body.dataset.monolithCharacter = state.activeId;
    return data;
  }

  function rebrand() {
    document.title = "Monolith AI";
    const identity = $(".deck-identity strong");
    const sub = $(".deck-identity span");
    if (identity) identity.textContent = "MONOLITH AI";
    if (sub) sub.textContent = `ACTIVE CHARACTER // ${state.activeCharacter.toUpperCase()}`;

    $$(".deck-footer span").forEach((node, index) => {
      if (index === 0) node.textContent = "MONOLITH CORE // LOCAL";
      if (index === 1) node.textContent = `${state.activeCharacter.toUpperCase()} LINK // STABLE`;
      if (index === 2) node.textContent = "EXCLUSIVE SCENE ROUTER // ONLINE";
    });

    const archiveHead = $(".v79-kb-head h2");
    const archiveSub = $(".v79-kb-head p");
    if (archiveHead) archiveHead.textContent = "Archives // Knowledge Vault";
    if (archiveSub) archiveSub.textContent = "Local memory vault // documents, scans, notes, audio and video intelligence.";

    const focusLabel = $("#telemetry-focus .telemetry-label");
    if (focusLabel) focusLabel.textContent = `${state.activeCharacter} Focus`;
  }

  function navButton(id, glyph, title, subtitle) {
    return `<button id="${id}" class="deck-nav-pod monolith-nav-pod" type="button"><span class="deck-pod-icon">${glyph}</span><span class="deck-pod-copy"><strong>${title}</strong><small>${subtitle}</small></span><span class="deck-pod-arrow" aria-hidden="true"></span></button>`;
  }

  function installMenu() {
    const list = $("#janeMenuPanel .deck-menu-list");
    if (!list) return false;

    if (!$("#monolithNavModel", list)) {
      list.insertAdjacentHTML(
        "beforeend",
        navButton("monolithNavModel", I.model, "Monolith Model", "Character identity // holographic renderer") +
        navButton("monolithNavVoice", I.voice, "Voice Module", "Local Piper dataset // model runtime") +
        navButton("monolithNavRpg", I.rpg, "RPG", "Starfinder 1e play // DM console")
      );
    }

    const bindings = [
      ["monolithNavModel", "model"],
      ["monolithNavVoice", "voice"],
      ["monolithNavRpg", "rpg"]
    ];
    bindings.forEach(([id, name]) => {
      const button = document.getElementById(id);
      if (!button) return;
      button.onclick = event => {
        event.preventDefault();
        event.stopPropagation();
        open(name);
      };
    });
    return true;
  }

  function shell(title, kicker, body) {
    return `<section class="monolith-module"><header class="monolith-module-head"><button class="monolith-return" type="button">${I.back}<span>RETURN</span></button><div class="monolith-module-title"><strong>${esc(title)}</strong><small>${esc(kicker)}</small></div><span class="monolith-live"><i></i>SYS_ON // LOCAL</span></header><div class="monolith-module-body">${body}</div></section>`;
  }

  function moduleRoot(name) {
    const route = MODULE_ROUTES[name];
    if (!route) return null;

    const runtime = window.MonolithSceneRuntime;
    if (runtime && typeof runtime.sceneFor === "function") return runtime.sceneFor(name);

    const host = document.getElementById("janeSceneHost");
    if (!host) return null;
    let root = host.querySelector(`:scope > [data-jane-scene="${route}"]`);
    if (!root) {
      root = document.createElement("section");
      root.className = "monolith-scene-root";
      root.dataset.janeScene = route;
      root.dataset.janeActive = "false";
      root.setAttribute("aria-hidden", "true");
      host.appendChild(root);
      window.JaneSceneRouter?.register?.(route, root);
    }
    return root;
  }

  function wireReturn(root) {
    const button = $(".monolith-return", root);
    if (button) button.onclick = close;
  }

  function open(name) {
    if (!MODULE_ROUTES[name]) return false;
    loadState();
    const root = moduleRoot(name);
    if (!root) {
      setTimeout(() => open(name), 80);
      return false;
    }

    state.screen = name;
    if (name === "model") renderModel(root);
    else if (name === "voice") renderVoice(root);
    else if (name === "rpg") renderRpg(root);
    wireReturn(root);

    if (window.MonolithSceneRuntime?.open) {
      window.MonolithSceneRuntime.open(name, { push: true });
    } else {
      window.JaneSceneRouter?.open?.(MODULE_ROUTES[name], { push: true });
    }
    return true;
  }

  function close(event) {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    state.screen = "";
    if (window.MonolithSceneRuntime?.back) return window.MonolithSceneRuntime.back();
    return window.JaneSceneRouter?.back?.();
  }

  function characterCards() {
    const chars = state.characters?.characters || [];
    return chars.map(character => {
      const id = esc(character.id);
      const name = esc(character.name);
      const status = esc(String(character.status || "").replaceAll("-", " "));
      const xp = Number(character.xp || 0);
      const level = Number(character.level || 1);
      const progress = Math.max(0, Math.min(100, xp % 100));
      const details = character.id === "female_jane"
        ? "ESTABLISHED PERSONALITY // GLB + PORTRAIT MATRIX"
        : "MALE BACKEND + ASSET SLOTS PROVISIONED // NATIVE MODEL PENDING";
      return `<button class="monolith-character-card ${character.id === state.activeId ? "active" : ""}" data-character="${id}" type="button"><span class="character-glyph">${I.user}</span><strong>${name}</strong><em>${status}</em><div class="character-level"><span>LV ${level}</span><i><b style="width:${progress}%"></b></i><span>${xp} XP</span></div><small>${details}</small></button>`;
    }).join("");
  }

  function renderModel(root = moduleRoot("model")) {
    if (!root) return;
    const source = $("#homeJaneModel")?.getAttribute("src") || "";
    const female = state.activeId === "female_jane";
    const viewer = female && source
      ? `<model-viewer id="monolithCharacterViewer" src="${esc(source)}" camera-controls auto-rotate interaction-prompt="none" shadow-intensity="1" shadow-softness=".7" exposure="1.1" camera-orbit="0deg 78deg 112%" camera-target="0m .52m 0m" field-of-view="23deg" rotation-per-second="9deg"></model-viewer>`
      : `<div class="monolith-pending-model"><div class="wire-orbit"></div>${I.model}<strong>MALE MODEL SLOT</strong><span>characters/male/model.glb</span><em>NATIVE BUILD PENDING</em></div>`;

    root.innerHTML = shell(
      "Monolith Model",
      "CHARACTER CONTROL // HOLOGRAPHIC RENDER MATRIX",
      `<div class="monolith-model-grid"><aside class="character-selector hardware-panel"><div class="panel-label"><span>01</span><strong>ACTIVE IDENTITY</strong></div>${characterCards()}<button id="monolithXpPulse" class="monolith-bar-action" type="button">AWARD +25 XP</button></aside><section class="model-reactor hardware-panel">${viewer}<div class="reactor-reticle ring-a"></div><div class="reactor-reticle ring-b"></div><div class="model-anchor a1"></div><div class="model-anchor a2"></div><div class="model-floor-readout"><span>HOLOGRAPHIC BODY</span><strong>${esc(state.activeCharacter.toUpperCase())}</strong></div></section><aside class="model-controls hardware-panel"><div class="panel-label"><span>02</span><strong>RENDER MATRIX</strong></div><label>EXPOSURE<input id="monolithExposure" type="range" min="0.5" max="2" step="0.05" value="1.1"></label><label>ROTATION<input id="monolithRotation" type="range" min="1" max="30" step="1" value="9"></label><div class="model-readout"><span>ACTIVE CORE</span><strong>${esc(state.activeCharacter)}</strong></div><div class="model-readout"><span>GLB PIPELINE</span><strong>${female ? "BOUND" : "SLOT READY"}</strong></div><div class="model-readout"><span>PERSONALITY</span><strong>${female ? "JANE // ESTABLISHED" : "MALE // PROVISIONED"}</strong></div><div class="model-readout"><span>RENDER MODE</span><strong>LOCAL WEBGL</strong></div></aside></div>`
    );
    wireReturn(root);

    $$("[data-character]", root).forEach(button => {
      button.onclick = () => {
        if (call("setActiveCharacter", button.dataset.character)) {
          loadState();
          rebrand();
          renderModel(root);
        }
      };
    });

    $("#monolithXpPulse", root)?.addEventListener("click", () => {
      call("addCharacterExperience", 25);
      loadState();
      renderModel(root);
    });
    $("#monolithExposure", root)?.addEventListener("input", event => {
      $("#monolithCharacterViewer", root)?.setAttribute("exposure", event.target.value);
    });
    $("#monolithRotation", root)?.addEventListener("input", event => {
      $("#monolithCharacterViewer", root)?.setAttribute("rotation-per-second", `${event.target.value}deg`);
    });
  }

  function voiceRows(voiceState) {
    const datasets = (voiceState.datasets || []).map(dataset => {
      const id = esc(dataset.id);
      return `<article><strong>${id}</strong><span>${Number(dataset.clips || 0)} WAV CLIPS</span><small>${dataset.metadata ? "METADATA LINKED" : "METADATA EMPTY"}</small></article>`;
    }).join("") || '<article><strong>NO DATASET YET</strong><span>Record or import WAV samples</span><small>LOCAL WORKSPACE EMPTY</small></article>';

    const models = (voiceState.models || []).map(model => {
      const id = esc(model.id);
      const ready = Boolean(model.runnable || (model.onnx && model.tokens));
      return `<article><strong>${id}</strong><span>${model.onnx ? "ONNX READY" : "ONNX MISSING"} // ${model.tokens ? "TOKENS READY" : "TOKENS MISSING"}</span><small>${model.config ? "CONFIG LINKED" : "CONFIG OPTIONAL"}</small><button data-voice-model="${id}" ${ready ? "" : "disabled"}>${model.active ? "ACTIVE // LOCAL" : ready ? "ACTIVATE LOCAL" : "CONVERSION REQUIRED"}</button></article>`;
    }).join("") || '<article><strong>NO LOCAL MODEL</strong><span>Import converted Piper ONNX + tokens</span><small>RUNTIME STANDBY</small></article>';

    return { datasets, models };
  }

  function renderVoice(root = moduleRoot("voice")) {
    if (!root) return;
    state.voice = parse(call("getVoiceWorkspace"), state.voice || {});
    const rows = voiceRows(state.voice);
    const activeModel = state.voice.activeModel ? `LOCAL MODEL // ${esc(state.voice.activeModel)}` : "LOCAL MODEL SLOT // EMPTY";
    const runtimeState = esc(String(state.voice.runtimeState || "inactive").toUpperCase());

    root.innerHTML = shell(
      "Voice Module",
      "OFFLINE PIPER DATASET // SHERPA LOCAL SPEECH",
      `<div class="monolith-voice-grid"><section class="voice-conversation-link hardware-panel"><div class="panel-label"><span>01</span><strong>VOICE CORE</strong></div><div class="voice-reactor"><div class="voice-orbit orbit-a"></div><div class="voice-orbit orbit-b"></div>${I.voice}<span>${esc(state.activeCharacter.toUpperCase())}</span><strong>CONVERSATION CORE</strong><em>${activeModel} // ${runtimeState}</em></div><button id="monolithOpenConversation" class="monolith-bar-action" type="button">OPEN ACTIVE CONVERSATION</button><p>Datasets and imported models remain in protected app storage across updates. Recording is local. Piper training/export stays offline.</p></section><section class="voice-dataset hardware-panel"><div class="panel-label"><span>02</span><strong>DATASET RECORDER</strong></div><label>DATASET ID<input id="voiceDataset" value="${esc(state.activeCharacter.toLowerCase().replace(/\s+/g, "_"))}"></label><label>TRANSCRIPT<textarea id="voiceTranscript" placeholder="Exact words spoken in this sample"></textarea></label><div class="voice-buttons"><button id="voiceRecord" type="button">${I.mic}<span>RECORD SAMPLE</span></button><button id="voiceImport" type="button">${I.upload}<span>IMPORT ASSETS</span></button></div><div id="voiceRecordState" class="voice-status">RECORDER STANDBY</div><div class="panel-subhead">DATASETS</div><div class="voice-list">${rows.datasets}</div></section><section class="voice-models hardware-panel"><div class="panel-label"><span>03</span><strong>PIPER MODEL TARGETS</strong></div><div class="voice-list">${rows.models}</div><div class="voice-framework"><strong>TRAINING FRAMEWORK</strong><span>TextyMcSpeechy-compatible WAV + metadata export structure</span><small>CAPTURE // OFFLINE TRAIN // SHERPA CONVERT // LOCAL SYSTEMIC SPEECH</small></div></section></div>`
    );
    wireReturn(root);

    $("#monolithOpenConversation", root)?.addEventListener("click", () => {
      state.screen = "";
      window.JaneSceneRouter?.open?.("chat", { initialPortrait: true });
    });

    const record = $("#voiceRecord", root);
    if (record) {
      record.onclick = () => {
        const status = $("#voiceRecordState", root);
        if (record.dataset.recording === "true") {
          const result = call("stopVoiceSample");
          record.dataset.recording = "false";
          record.querySelector("span").textContent = "RECORD SAMPLE";
          if (status) status.textContent = String(result).startsWith("ERROR:") ? result : "SAMPLE SEALED // METADATA UPDATED";
          setTimeout(() => renderVoice(root), 500);
        } else {
          const result = call("startVoiceSample", $("#voiceDataset", root)?.value || "", $("#voiceTranscript", root)?.value || "");
          if (String(result).startsWith("ERROR:")) {
            if (status) status.textContent = result;
            return;
          }
          record.dataset.recording = "true";
          record.querySelector("span").textContent = "STOP + SEAL SAMPLE";
          if (status) status.textContent = `CAPTURING // ${result}`;
        }
      };
    }

    $("#voiceImport", root)?.addEventListener("click", () => call("pickVoiceAssets"));
    $$('[data-voice-model]', root).forEach(button => {
      button.onclick = () => {
        call("setActiveVoiceModel", button.dataset.voiceModel);
        setTimeout(() => renderVoice(root), 250);
      };
    });

    window.MonolithVoiceRuntimePatch?.apply?.();
  }

  window.MonolithVoiceWorkspaceChanged = data => {
    state.voice = typeof data === "string" ? parse(data, {}) : (data || {});
    if (state.screen === "voice") renderVoice();
  };

  window.MonolithCharacterChanged = data => {
    const parsed = typeof data === "string" ? parse(data, {}) : (data || {});
    state.characters = parsed;
    state.activeId = parsed.activeId || state.activeId;
    state.activeCharacter = parsed.activeName || state.activeCharacter;
    rebrand();
    if (state.screen === "model") renderModel();
  };

  function sheetDefault() {
    return {
      name: "", species: "", theme: "", klass: "", level: 1, xp: 0,
      str: 10, dex: 10, con: 10, int: 10, wis: 10, cha: 10,
      hp: 10, sp: 10, rp: 1, eac: 10, kac: 10, bab: 0,
      fort: 0, ref: 0, will: 0, initiative: 0, skills: {}
    };
  }

  function loadSheet() {
    try {
      const parsed = JSON.parse(localStorage.getItem("monolith.sf1.sheet") || "{}");
      return Object.assign(sheetDefault(), parsed, { skills: Object.assign({}, parsed.skills || {}) });
    } catch (_) {
      return sheetDefault();
    }
  }

  function saveSheet(sheet) {
    localStorage.setItem("monolith.sf1.sheet", JSON.stringify(sheet));
  }

  function mod(score) {
    return Math.floor((Number(score || 10) - 10) / 2);
  }

  function fairDie(sides) {
    const count = Math.max(2, Math.floor(Number(sides || 20)));
    if (window.crypto && typeof window.crypto.getRandomValues === "function") {
      const max = Math.floor(0x100000000 / count) * count;
      const array = new Uint32Array(1);
      let value;
      do {
        window.crypto.getRandomValues(array);
        value = array[0];
      } while (value >= max);
      return (value % count) + 1;
    }
    return Math.floor(Math.random() * count) + 1;
  }

  function renderRpg(root = moduleRoot("rpg")) {
    if (!root) return;
    const sheet = loadSheet();
    const abilities = ["str", "dex", "con", "int", "wis", "cha"].map(key => {
      const score = Number(sheet[key] || 10);
      const modifier = mod(score);
      return `<label>${key.toUpperCase()}<input data-sheet="${key}" type="number" value="${score}"><small>MOD <b data-mod="${key}">${modifier >= 0 ? "+" : ""}${modifier}</b></small></label>`;
    }).join("");

    const skills = SF1_SKILLS.map(([name, ability]) => `<label class="sf-skill"><span>${esc(name)}<small>${ability.toUpperCase()}</small></span><input data-skill="${esc(name)}" type="number" value="${Number(sheet.skills?.[name] || 0)}"></label>`).join("");

    root.innerHTML = shell(
      "RPG",
      "STARFINDER 1E TABLETOP ENGINE",
      `<div class="monolith-rpg-grid"><section class="sf-sheet hardware-panel"><header><strong>CHARACTER MATRIX</strong><span>AUTO-SAVE // LOCAL</span></header><div class="sf-identity"><input data-sheet="name" placeholder="CHARACTER" value="${esc(sheet.name)}"><input data-sheet="species" placeholder="SPECIES" value="${esc(sheet.species)}"><input data-sheet="theme" placeholder="THEME" value="${esc(sheet.theme)}"><input data-sheet="klass" placeholder="CLASS" value="${esc(sheet.klass)}"><label>LV<input data-sheet="level" type="number" value="${Number(sheet.level || 1)}"></label><label>XP<input data-sheet="xp" type="number" value="${Number(sheet.xp || 0)}"></label></div><div class="sf-abilities">${abilities}</div><div class="sf-vitals"><label>HP<input data-sheet="hp" type="number" value="${Number(sheet.hp || 0)}"></label><label>SP<input data-sheet="sp" type="number" value="${Number(sheet.sp || 0)}"></label><label>RP<input data-sheet="rp" type="number" value="${Number(sheet.rp || 0)}"></label><label>EAC<input data-sheet="eac" type="number" value="${Number(sheet.eac || 0)}"></label><label>KAC<input data-sheet="kac" type="number" value="${Number(sheet.kac || 0)}"></label><label>BAB<input data-sheet="bab" type="number" value="${Number(sheet.bab || 0)}"></label><label>INIT<input data-sheet="initiative" type="number" value="${Number(sheet.initiative || 0)}"></label></div><div class="sf-saves"><label>FORT<input data-sheet="fort" type="number" value="${Number(sheet.fort || 0)}"></label><label>REF<input data-sheet="ref" type="number" value="${Number(sheet.ref || 0)}"></label><label>WILL<input data-sheet="will" type="number" value="${Number(sheet.will || 0)}"></label></div><details><summary>SKILLS MATRIX</summary><div class="sf-skills">${skills}</div></details></section><section class="sf-dice-console hardware-panel"><header><strong>HOLOGRAPHIC DICE CORE</strong><span>CRYPTO RNG</span></header><div class="dice-stage"><div id="monolithD20" class="monolith-d20"><i></i><b id="monolithDieFace">20</b></div></div><div class="dice-controls"><select id="diceSides"><option>20</option><option>4</option><option>6</option><option>8</option><option>10</option><option>12</option><option>100</option></select><input id="diceMod" type="number" value="0" aria-label="Modifier"><button id="diceRoll" type="button">${I.dice}<span>ROLL</span></button></div><div id="diceResult" class="dice-result">AWAITING ROLL</div><div class="quick-rolls"><button data-roll="initiative" type="button">INITIATIVE</button><button data-roll="fort" type="button">FORT</button><button data-roll="ref" type="button">REF</button><button data-roll="will" type="button">WILL</button><button data-roll="attack" type="button">ATTACK</button></div><label class="check-select">SKILL CHECK<select id="skillRoll">${SF1_SKILLS.map(([name]) => `<option>${esc(name)}</option>`).join("")}</select><button id="rollSkill" type="button">ROLL CHECK</button></label><div id="rollLog" class="roll-log"></div></section><section class="sf-dm-console hardware-panel"><header><strong>DM CONTROL</strong><span>ENCOUNTER MATRIX</span></header><div class="dm-add"><input id="dmName" placeholder="COMBATANT"><input id="dmInit" type="number" placeholder="INIT"><button id="dmAdd" type="button">ADD</button></div><div id="dmInitiative" class="dm-initiative"></div><label>ENCOUNTER NOTES<textarea id="dmNotes"></textarea></label><label>TARGET KAC<input id="dmTargetKac" type="number" value="10"></label><label>TARGET EAC<input id="dmTargetEac" type="number" value="10"></label><div class="rules-readout"><strong>ENGINE HOOKS</strong><span>d20 checks // ability modifiers // BAB // saves // defenses // skills // initiative // combat log</span><small>Imported campaign and rules references remain available through Archives.</small></div></section></div>`
    );
    wireReturn(root);

    const current = loadSheet();
    $$('[data-sheet]', root).forEach(input => {
      input.onchange = () => {
        current[input.dataset.sheet] = input.type === "number" ? Number(input.value) : input.value;
        saveSheet(current);
        const modNode = $(`[data-mod="${input.dataset.sheet}"]`, root);
        if (modNode) {
          const value = mod(input.value);
          modNode.textContent = `${value >= 0 ? "+" : ""}${value}`;
        }
      };
    });
    $$('[data-skill]', root).forEach(input => {
      input.onchange = () => {
        current.skills = current.skills || {};
        current.skills[input.dataset.skill] = Number(input.value);
        saveSheet(current);
      };
    });

    const log = (label, raw, bonus, total) => {
      const area = $("#rollLog", root);
      if (!area) return;
      area.insertAdjacentHTML("afterbegin", `<div><strong>${esc(label)}</strong><span>${raw} ${bonus >= 0 ? "+" : ""}${bonus} = ${total}</span></div>`);
      while (area.children.length > 12) area.lastElementChild.remove();
    };

    const roll = (label, sides, bonus) => {
      const raw = fairDie(sides);
      const numericBonus = Number(bonus || 0);
      const total = raw + numericBonus;
      const die = $("#monolithD20", root);
      if (die) {
        die.classList.remove("rolling");
        void die.offsetWidth;
        die.classList.add("rolling");
      }
      const face = $("#monolithDieFace", root);
      const result = $("#diceResult", root);
      if (face) face.textContent = raw;
      if (result) result.textContent = `${String(label).toUpperCase()} // ${total}`;
      log(label, raw, numericBonus, total);
      return total;
    };

    $("#diceRoll", root)?.addEventListener("click", () => {
      roll(`D${$("#diceSides", root)?.value || 20}`, Number($("#diceSides", root)?.value || 20), Number($("#diceMod", root)?.value || 0));
    });

    $$('[data-roll]', root).forEach(button => {
      button.onclick = () => {
        const key = button.dataset.roll;
        let bonus = 0;
        if (key === "initiative") bonus = Number(current.initiative || 0) + mod(current.dex);
        else if (key === "attack") bonus = Number(current.bab || 0) + mod(current.str);
        else bonus = Number(current[key] || 0);
        roll(key, 20, bonus);
      };
    });

    $("#rollSkill", root)?.addEventListener("click", () => {
      const name = $("#skillRoll", root)?.value || "Perception";
      const definition = SF1_SKILLS.find(entry => entry[0] === name);
      const rank = Number(current.skills?.[name] || 0);
      const ability = definition?.[1];
      const bonus = rank + (ability && ability !== "var" ? mod(current[ability]) : 0);
      roll(name, 20, bonus);
    });

    const dmKey = "monolith.sf1.dm";
    let dm;
    try {
      dm = JSON.parse(localStorage.getItem(dmKey) || '{"rows":[],"notes":""}');
    } catch (_) {
      dm = { rows: [], notes: "" };
    }
    if (!Array.isArray(dm.rows)) dm.rows = [];

    const renderDm = () => {
      const area = $("#dmInitiative", root);
      if (!area) return;
      const sorted = dm.rows.map((row, index) => ({ row, index })).sort((a, b) => Number(b.row.init || 0) - Number(a.row.init || 0));
      area.innerHTML = sorted.map(entry => `<div><strong>${Number(entry.row.init || 0)}</strong><span>${esc(entry.row.name)}</span><button data-dm-remove="${entry.index}" type="button">REMOVE</button></div>`).join("");
      $$('[data-dm-remove]', area).forEach(button => {
        button.onclick = () => {
          dm.rows.splice(Number(button.dataset.dmRemove), 1);
          localStorage.setItem(dmKey, JSON.stringify(dm));
          renderDm();
        };
      });
    };

    const notes = $("#dmNotes", root);
    if (notes) {
      notes.value = dm.notes || "";
      notes.onchange = event => {
        dm.notes = event.target.value;
        localStorage.setItem(dmKey, JSON.stringify(dm));
      };
    }

    $("#dmAdd", root)?.addEventListener("click", () => {
      const name = $("#dmName", root)?.value.trim() || "";
      if (!name) return;
      dm.rows.push({ name, init: Number($("#dmInit", root)?.value || 0) });
      localStorage.setItem(dmKey, JSON.stringify(dm));
      $("#dmName", root).value = "";
      renderDm();
    });
    renderDm();
  }

  function assistToPrompt() {
    const snapshot = parse(call("getAssistSnapshot"), {});
    const bits = [];
    (snapshot.windows || []).forEach(windowState => {
      (windowState.nodes || []).forEach(node => {
        if (node.text) bits.push(node.text);
        else if (node.description) bits.push(node.description);
      });
    });
    return bits.join(" | ").slice(0, 3500);
  }

  window.MonolithReceiveLaunchMode = mode => {
    if (!mode || mode === "home") return;
    setTimeout(() => {
      if (mode === "search") {
        window.JaneSceneRouter?.open?.("chat", { initialPortrait: true });
        $("#userInput")?.focus();
      } else if (mode === "assistant_context") {
        window.JaneSceneRouter?.open?.("chat", { initialPortrait: true });
        const input = $("#userInput");
        const context = assistToPrompt();
        if (input && context) {
          input.value = `Use this current-screen context as working context, then answer my request: ${context}\n\n`;
          input.focus();
        }
      } else if (mode === "voice") {
        window.JaneSceneRouter?.open?.("chat", { initialPortrait: true });
        try { window.AndroidJane?.startVoice?.(); } catch (_) {}
      } else if (mode === "image") {
        try {
          window.JaneOpenImage?.();
        } catch (_) {
          window.JaneSceneRouter?.open?.("chat", { initialPortrait: true });
        }
      }
    }, 350);
  };

  function installVaultBadge() {
    const head = $(".v79-kb-head");
    if (!head || $(".monolith-vault-badge", head)) return;
    const badge = document.createElement("div");
    badge.className = "monolith-vault-badge";
    badge.innerHTML = `${I.archive}<span>KNOWLEDGE VAULT</span><small>VIEW // READ // DELETE // LOCAL MEMORY</small>`;
    head.appendChild(badge);
  }

  function refresh() {
    loadState();
    rebrand();
    installMenu();
    installVaultBadge();
    window.MonolithFinalUi?.refresh?.();
    return true;
  }

  window.MonolithCore = { version: VERSION, refresh, open, close };

  let tries = 0;
  const timer = setInterval(() => {
    tries += 1;
    refresh();
    if ($("#janeMenuPanel") || tries > 60) clearInterval(timer);
  }, 150);

  refresh();
})();
