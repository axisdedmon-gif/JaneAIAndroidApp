(function () {
  "use strict";
  if (window.MonolithCore) { window.MonolithCore.refresh(); return; }

  const $ = (s, r=document) => r.querySelector(s);
  const $$ = (s, r=document) => Array.from(r.querySelectorAll(s));
  const state = { activeCharacter: "Jane", activeId: "female_jane", overlay: null, screen: "", voice: null, characters: null };
  const SF1_SKILLS = [
    ["Acrobatics","dex"],["Athletics","str"],["Bluff","cha"],["Computers","int"],["Culture","int"],
    ["Diplomacy","cha"],["Disguise","cha"],["Engineering","int"],["Intimidate","cha"],["Life Science","int"],
    ["Medicine","int"],["Mysticism","wis"],["Perception","wis"],["Physical Science","int"],["Piloting","dex"],
    ["Profession","var"],["Sense Motive","wis"],["Sleight of Hand","dex"],["Stealth","dex"],["Survival","wis"]
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

  function call(method, ...args) {
    try {
      if (!window.AndroidMonolith || typeof window.AndroidMonolith[method] !== "function") return null;
      return window.AndroidMonolith[method](...args);
    } catch (e) { console.warn("[Monolith]", method, e); return null; }
  }

  function parse(value, fallback={}) {
    try { return typeof value === "string" ? JSON.parse(value) : (value || fallback); } catch (_) { return fallback; }
  }

  function loadState() {
    const data = parse(call("getSystemState"), {});
    const chars = data.characters || parse(call("getCharacterState"), {});
    state.characters = chars;
    state.activeCharacter = chars.activeName || "Jane";
    state.activeId = chars.activeId || "female_jane";
    state.voice = data.voice || parse(call("getVoiceWorkspace"), {});
    document.body.dataset.monolithCharacter = state.activeId;
    return data;
  }

  function rebrand() {
    document.title = "Monolith AI";
    const identity = $(".deck-identity strong");
    const sub = $(".deck-identity span");
    if (identity) identity.textContent = "Monolith AI";
    if (sub) sub.textContent = `ACTIVE CHARACTER // ${state.activeCharacter.toUpperCase()}`;
    $$(".deck-footer span").forEach((n, i) => {
      if (i === 0) n.textContent = "MONOLITH CORE // ACTIVE";
      if (i === 1) n.textContent = `${state.activeCharacter.toUpperCase()} LINK // STABLE`;
    });
    const exact = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    let n;
    while ((n = exact.nextNode())) {
      if (n.parentElement && /^(SCRIPT|STYLE|TEXTAREA)$/.test(n.parentElement.tagName)) continue;
      if (n.nodeValue && n.nodeValue.includes("Jane AI Assistant")) n.nodeValue = n.nodeValue.replaceAll("Jane AI Assistant", "Monolith AI");
    }
    const archiveHead = $(".v79-kb-head h2");
    const archiveSub = $(".v79-kb-head p");
    if (archiveHead) archiveHead.textContent = "Archives // Knowledge Vault";
    if (archiveSub) archiveSub.textContent = "Local memory vault // documents, scans, notes and preserved media references.";
  }

  function navButton(id, glyph, title, subtitle) {
    return `<button id="${id}" class="deck-nav-pod monolith-nav-pod" type="button"><span class="deck-pod-icon">${glyph}</span><span class="deck-pod-copy"><strong>${title}</strong><small>${subtitle}</small></span><span class="deck-pod-arrow"></span></button>`;
  }

  function installMenu() {
    const list = $("#janeMenuPanel .deck-menu-list");
    if (!list || $("#monolithNavModel", list)) return;
    list.insertAdjacentHTML("beforeend",
      navButton("monolithNavModel", I.model, "Monolith Model", "Character identity and progression") +
      navButton("monolithNavVoice", I.voice, "Voice Module", "Local dataset and model workspace") +
      navButton("monolithNavRpg", I.rpg, "RPG", "Starfinder 1e play and DM console")
    );
    $("#monolithNavModel").onclick = () => open("model");
    $("#monolithNavVoice").onclick = () => open("voice");
    $("#monolithNavRpg").onclick = () => open("rpg");
  }

  function shell(title, kicker, body) {
    return `<section class="monolith-module"><header class="monolith-module-head"><button class="monolith-return" type="button">${I.back}<span>RETURN</span></button><div><strong>${title}</strong><small>${kicker}</small></div><span class="monolith-live">SYS_ON // LOCAL</span></header><div class="monolith-module-body">${body}</div></section>`;
  }

  function ensureOverlay() {
    if (state.overlay && document.contains(state.overlay)) return state.overlay;
    const overlay = document.createElement("div");
    overlay.id = "monolithModuleOverlay";
    overlay.hidden = true;
    document.body.appendChild(overlay);
    state.overlay = overlay;
    return overlay;
  }

  function close() {
    if (!state.overlay) return;
    state.overlay.hidden = true;
    state.overlay.innerHTML = "";
    state.screen = "";
  }

  function open(name) {
    loadState();
    const overlay = ensureOverlay();
    state.screen = name;
    overlay.hidden = false;
    if (name === "model") renderModel();
    else if (name === "voice") renderVoice();
    else if (name === "rpg") renderRpg();
    overlay.querySelector(".monolith-return")?.addEventListener("click", close);
  }

  function characterCards() {
    const chars = state.characters?.characters || [];
    return chars.map(c => `<button class="monolith-character-card ${c.id===state.activeId?'active':''}" data-character="${c.id}" type="button"><span class="character-glyph">${I.user}</span><strong>${c.name}</strong><em>${String(c.status).replaceAll('-',' ')}</em><div class="character-level"><span>LV ${c.level || 1}</span><i><b style="width:${Math.min(100, Number(c.xp||0)%100)}%"></b></i><span>${c.xp || 0} XP</span></div><small>${c.id==='female_jane'?'ESTABLISHED PERSONALITY // GLB + PORTRAIT MATRIX':'MALE BACKEND + ASSET SLOTS PROVISIONED // NATIVE BUILD PENDING'}</small></button>`).join("");
  }

  function renderModel() {
    const source = $("#homeJaneModel")?.getAttribute("src") || "";
    const female = state.activeId === "female_jane";
    const viewer = female && source ? `<model-viewer id="monolithCharacterViewer" src="${source}" camera-controls auto-rotate interaction-prompt="none" shadow-intensity="1" shadow-softness=".7" exposure="1.1" camera-orbit="0deg 78deg 112%" camera-target="0m .52m 0m" field-of-view="23deg"></model-viewer>` : `<div class="monolith-pending-model"><div class="wire-orbit"></div>${I.model}<strong>MALE MODEL SLOT</strong><span>characters/male/model.glb</span><em>NATIVE BUILD PENDING</em></div>`;
    state.overlay.innerHTML = shell("Monolith Model", "CHARACTER CONTROL // PROGRESSION MATRIX", `<div class="monolith-model-grid"><aside class="character-selector"><h3>ACTIVE IDENTITY</h3>${characterCards()}<button id="monolithXpPulse" class="monolith-action" type="button">SIMULATE +25 XP</button></aside><section class="model-reactor">${viewer}<div class="model-anchor a1"></div><div class="model-anchor a2"></div></section><aside class="model-controls"><h3>RENDER MATRIX</h3><label>EXPOSURE <input id="monolithExposure" type="range" min="0.5" max="2" step="0.05" value="1.1"></label><label>ROTATION <input id="monolithRotation" type="range" min="1" max="30" step="1" value="9"></label><div class="model-readout"><span>ACTIVE CORE</span><strong>${state.activeCharacter}</strong></div><div class="model-readout"><span>GLB PIPELINE</span><strong>${female?'BOUND':'SLOT READY'}</strong></div><div class="model-readout"><span>PERSONALITY</span><strong>${female?'JANE // ESTABLISHED':'MALE // PENDING'}</strong></div></aside></div>`);
    state.overlay.querySelector(".monolith-return").onclick = close;
    $$("[data-character]", state.overlay).forEach(btn => btn.onclick = () => {
      if (call("setActiveCharacter", btn.dataset.character)) { loadState(); rebrand(); renderModel(); }
    });
    $("#monolithXpPulse", state.overlay).onclick = () => { call("addCharacterExperience", 25); loadState(); renderModel(); };
    $("#monolithExposure", state.overlay)?.addEventListener("input", e => $("#monolithCharacterViewer", state.overlay)?.setAttribute("exposure", e.target.value));
    $("#monolithRotation", state.overlay)?.addEventListener("input", e => $("#monolithCharacterViewer", state.overlay)?.setAttribute("rotation-per-second", `${e.target.value}deg`));
  }

  function voiceRows(v) {
    const datasets = (v.datasets || []).map(d => `<article><strong>${d.id}</strong><span>${d.clips} WAV CLIPS</span><small>${d.metadata?'METADATA LINKED':'METADATA EMPTY'}</small></article>`).join("") || '<article><strong>NO DATASET YET</strong><span>Record or import WAV samples</span></article>';
    const models = (v.models || []).map(m => `<article><strong>${m.id}</strong><span>${m.onnx?'ONNX READY':'ONNX MISSING'} // ${m.config?'CONFIG READY':'CONFIG MISSING'}</span><small>${m.tokens?'TOKENS READY':'TOKENS MAY REQUIRE CONVERSION'}</small><button data-voice-model="${m.id}" ${m.onnx?'':'disabled'}>${m.active?'ACTIVE':'ACTIVATE'}</button></article>`).join("") || '<article><strong>NO LOCAL MODEL</strong><span>Import Piper .onnx + .onnx.json targets</span></article>';
    return {datasets, models};
  }

  function renderVoice() {
    state.voice = parse(call("getVoiceWorkspace"), state.voice || {});
    const rows = voiceRows(state.voice);
    state.overlay.innerHTML = shell("Voice Module", "OFFLINE PIPER DATASET WORKSPACE", `<div class="monolith-voice-grid"><section class="voice-conversation-link"><div class="voice-reactor">${I.voice}<span>${state.activeCharacter.toUpperCase()}</span><strong>CONVERSATION CORE</strong><em>${state.voice.activeModel?'LOCAL MODEL // '+state.voice.activeModel:'LOCAL MODEL SLOT // EMPTY'}</em></div><button id="monolithOpenConversation" class="monolith-action">OPEN ACTIVE CONVERSATION</button><p>Voice datasets and imported models remain in protected external app storage across APK updates. Training export follows the Piper WAV + metadata.csv convention.</p></section><section class="voice-dataset"><h3>DATASET RECORDER</h3><label>DATASET ID<input id="voiceDataset" value="${state.activeCharacter.toLowerCase().replace(/\s+/g,'_')}"></label><label>TRANSCRIPT<textarea id="voiceTranscript" placeholder="Exact words spoken in this sample"></textarea></label><div class="voice-buttons"><button id="voiceRecord">${I.mic}<span>RECORD SAMPLE</span></button><button id="voiceImport">${I.upload}<span>IMPORT ASSETS</span></button></div><div id="voiceRecordState" class="voice-status">RECORDER STANDBY</div><h3>DATASETS</h3><div class="voice-list">${rows.datasets}</div></section><section class="voice-models"><h3>PIPER MODEL TARGETS</h3><div class="voice-list">${rows.models}</div><div class="voice-framework"><strong>TRAINING FRAMEWORK</strong><span>TextyMcSpeechy-compatible dataset/export structure</span><small>ON-DEVICE DATASET CAPTURE // EXTERNAL OFFLINE TRAINING // ANDROID RUNTIME ADAPTER SLOT</small></div></section></div>`);
    state.overlay.querySelector(".monolith-return").onclick = close;
    $("#monolithOpenConversation", state.overlay).onclick = () => { close(); window.JaneSceneRouter?.open?.("chat", {initialPortrait:true}); };
    const record = $("#voiceRecord", state.overlay);
    record.onclick = () => {
      if (record.dataset.recording === "true") {
        const result = call("stopVoiceSample");
        record.dataset.recording = "false"; record.querySelector("span").textContent = "RECORD SAMPLE";
        $("#voiceRecordState", state.overlay).textContent = String(result).startsWith("ERROR:") ? result : "SAMPLE SEALED // METADATA UPDATED";
        setTimeout(renderVoice, 500);
      } else {
        const result = call("startVoiceSample", $("#voiceDataset", state.overlay).value, $("#voiceTranscript", state.overlay).value);
        if (String(result).startsWith("ERROR:")) { $("#voiceRecordState", state.overlay).textContent = result; return; }
        record.dataset.recording = "true"; record.querySelector("span").textContent = "STOP + SEAL SAMPLE";
        $("#voiceRecordState", state.overlay).textContent = `CAPTURING // ${result}`;
      }
    };
    $("#voiceImport", state.overlay).onclick = () => call("pickVoiceAssets");
    $$('[data-voice-model]', state.overlay).forEach(btn => btn.onclick = () => { call("setActiveVoiceModel", btn.dataset.voiceModel); setTimeout(renderVoice, 250); });
  }

  window.MonolithVoiceWorkspaceChanged = data => { state.voice = data; if (state.screen === "voice") renderVoice(); };
  window.MonolithCharacterChanged = data => { state.characters = data; state.activeId=data.activeId; state.activeCharacter=data.activeName; rebrand(); if(state.screen==="model")renderModel(); };

  function sheetDefault() {
    return {name:"",species:"",theme:"",klass:"",level:1,xp:0,str:10,dex:10,con:10,int:10,wis:10,cha:10,hp:10,sp:10,rp:1,eac:10,kac:10,bab:0,fort:0,ref:0,will:0,initiative:0,skills:{}};
  }
  function loadSheet() { try { return Object.assign(sheetDefault(), JSON.parse(localStorage.getItem("monolith.sf1.sheet")||"{}")); } catch(_){return sheetDefault();} }
  function saveSheet(sheet) { localStorage.setItem("monolith.sf1.sheet", JSON.stringify(sheet)); }
  function mod(score) { return Math.floor((Number(score||10)-10)/2); }
  function fairDie(sides) {
    const max = Math.floor(0x100000000 / sides) * sides;
    const a = new Uint32Array(1); let x;
    do { crypto.getRandomValues(a); x=a[0]; } while(x>=max);
    return (x%sides)+1;
  }

  function renderRpg() {
    const s = loadSheet();
    const abilities = ["str","dex","con","int","wis","cha"].map(k => `<label>${k.toUpperCase()}<input data-sheet="${k}" type="number" value="${s[k]}"><small>MOD <b data-mod="${k}">${mod(s[k])>=0?'+':''}${mod(s[k])}</b></small></label>`).join("");
    const skills = SF1_SKILLS.map(([name,ab]) => `<label class="sf-skill"><span>${name}<small>${ab.toUpperCase()}</small></span><input data-skill="${name}" type="number" value="${Number(s.skills?.[name]||0)}"></label>`).join("");
    state.overlay.innerHTML = shell("RPG", "STARFINDER 1E TABLETOP ENGINE", `<div class="monolith-rpg-grid"><section class="sf-sheet"><header><strong>CHARACTER MATRIX</strong><span>AUTO-SAVE // LOCAL</span></header><div class="sf-identity"><input data-sheet="name" placeholder="CHARACTER" value="${s.name}"><input data-sheet="species" placeholder="SPECIES" value="${s.species}"><input data-sheet="theme" placeholder="THEME" value="${s.theme}"><input data-sheet="klass" placeholder="CLASS" value="${s.klass}"><label>LV<input data-sheet="level" type="number" value="${s.level}"></label><label>XP<input data-sheet="xp" type="number" value="${s.xp}"></label></div><div class="sf-abilities">${abilities}</div><div class="sf-vitals"><label>HP<input data-sheet="hp" type="number" value="${s.hp}"></label><label>SP<input data-sheet="sp" type="number" value="${s.sp}"></label><label>RP<input data-sheet="rp" type="number" value="${s.rp}"></label><label>EAC<input data-sheet="eac" type="number" value="${s.eac}"></label><label>KAC<input data-sheet="kac" type="number" value="${s.kac}"></label><label>BAB<input data-sheet="bab" type="number" value="${s.bab}"></label><label>INIT<input data-sheet="initiative" type="number" value="${s.initiative}"></label></div><div class="sf-saves"><label>FORT<input data-sheet="fort" type="number" value="${s.fort}"></label><label>REF<input data-sheet="ref" type="number" value="${s.ref}"></label><label>WILL<input data-sheet="will" type="number" value="${s.will}"></label></div><details><summary>SKILLS MATRIX</summary><div class="sf-skills">${skills}</div></details></section><section class="sf-dice-console"><header><strong>HOLOGRAPHIC DICE CORE</strong><span>CRYPTO RNG</span></header><div class="dice-stage"><div id="monolithD20" class="monolith-d20"><i></i><b id="monolithDieFace">20</b></div></div><div class="dice-controls"><select id="diceSides"><option>20</option><option>4</option><option>6</option><option>8</option><option>10</option><option>12</option><option>100</option></select><input id="diceMod" type="number" value="0" aria-label="Modifier"><button id="diceRoll">${I.dice}<span>ROLL</span></button></div><div id="diceResult" class="dice-result">AWAITING ROLL</div><div class="quick-rolls"><button data-roll="initiative">INITIATIVE</button><button data-roll="fort">FORT</button><button data-roll="ref">REF</button><button data-roll="will">WILL</button><button data-roll="attack">ATTACK</button></div><label class="check-select">SKILL CHECK<select id="skillRoll">${SF1_SKILLS.map(([n])=>`<option>${n}</option>`).join('')}</select><button id="rollSkill">ROLL CHECK</button></label><div id="rollLog" class="roll-log"></div></section><section class="sf-dm-console"><header><strong>DM CONTROL</strong><span>ENCOUNTER MATRIX</span></header><div class="dm-add"><input id="dmName" placeholder="COMBATANT"><input id="dmInit" type="number" placeholder="INIT"><button id="dmAdd">ADD</button></div><div id="dmInitiative" class="dm-initiative"></div><label>ENCOUNTER NOTES<textarea id="dmNotes"></textarea></label><label>TARGET KAC<input id="dmTargetKac" type="number" value="10"></label><label>TARGET EAC<input id="dmTargetEac" type="number" value="10"></label><div class="rules-readout"><strong>ENGINE HOOKS</strong><span>d20 checks // ability modifiers // BAB // saves // defenses // skills // initiative // combat log</span><small>Rules text is not duplicated; imported campaign/rules references can live in Archives.</small></div></section></div>`);
    state.overlay.querySelector(".monolith-return").onclick = close;
    const current = loadSheet();
    $$('[data-sheet]', state.overlay).forEach(inp => inp.onchange = () => { current[inp.dataset.sheet] = inp.type==='number'?Number(inp.value):inp.value; saveSheet(current); if(inp.dataset.mod){} $(`[data-mod="${inp.dataset.sheet}"]`,state.overlay)?.replaceChildren(document.createTextNode(`${mod(inp.value)>=0?'+':''}${mod(inp.value)}`)); });
    $$('[data-skill]', state.overlay).forEach(inp => inp.onchange = () => { current.skills=current.skills||{}; current.skills[inp.dataset.skill]=Number(inp.value); saveSheet(current); });
    const log = (label, raw, bonus, total) => { const area=$("#rollLog",state.overlay); area.insertAdjacentHTML('afterbegin',`<div><strong>${label}</strong><span>${raw} ${bonus>=0?'+':''}${bonus} = ${total}</span></div>`); while(area.children.length>12)area.lastElementChild.remove(); };
    const roll = (label,sides,bonus) => { const raw=fairDie(sides), total=raw+Number(bonus||0); const d=$("#monolithD20",state.overlay); d.classList.remove('rolling'); void d.offsetWidth; d.classList.add('rolling'); $("#monolithDieFace",state.overlay).textContent=raw; $("#diceResult",state.overlay).textContent=`${label.toUpperCase()} // ${total}`; log(label,raw,Number(bonus||0),total); return total; };
    $("#diceRoll",state.overlay).onclick=()=>roll(`D${$("#diceSides",state.overlay).value}`,Number($("#diceSides",state.overlay).value),Number($("#diceMod",state.overlay).value));
    $$('[data-roll]',state.overlay).forEach(btn=>btn.onclick=()=>{ const key=btn.dataset.roll; let bonus=0; if(key==='initiative')bonus=Number(current.initiative||0)+mod(current.dex); else if(key==='attack')bonus=Number(current.bab||0)+mod(current.str); else bonus=Number(current[key]||0); roll(key,20,bonus); });
    $("#rollSkill",state.overlay).onclick=()=>{ const n=$("#skillRoll",state.overlay).value; const def=SF1_SKILLS.find(x=>x[0]===n); const rank=Number(current.skills?.[n]||0); const ab=def?.[1]; const bonus=rank+(ab&&ab!=='var'?mod(current[ab]):0); roll(n,20,bonus); };
    const dmKey="monolith.sf1.dm"; let dm; try{dm=JSON.parse(localStorage.getItem(dmKey)||'{"rows":[],"notes":""}')}catch(_){dm={rows:[],notes:""}};
    const renderDm=()=>{ const area=$("#dmInitiative",state.overlay); area.innerHTML=(dm.rows||[]).sort((a,b)=>b.init-a.init).map((r,i)=>`<div><strong>${r.init}</strong><span>${r.name}</span><button data-dm-remove="${i}">REMOVE</button></div>`).join(''); $$('[data-dm-remove]',area).forEach(b=>b.onclick=()=>{dm.rows.splice(Number(b.dataset.dmRemove),1);localStorage.setItem(dmKey,JSON.stringify(dm));renderDm();}); };
    $("#dmNotes",state.overlay).value=dm.notes||""; $("#dmNotes",state.overlay).onchange=e=>{dm.notes=e.target.value;localStorage.setItem(dmKey,JSON.stringify(dm));};
    $("#dmAdd",state.overlay).onclick=()=>{ const name=$("#dmName",state.overlay).value.trim(); if(!name)return; dm.rows=dm.rows||[]; dm.rows.push({name,init:Number($("#dmInit",state.overlay).value||0)}); localStorage.setItem(dmKey,JSON.stringify(dm)); $("#dmName",state.overlay).value='';renderDm();}; renderDm();
  }

  function assistToPrompt() {
    const snap = parse(call("getAssistSnapshot"), {});
    const bits = [];
    (snap.windows||[]).forEach(w => (w.nodes||[]).forEach(n => { if(n.text) bits.push(n.text); else if(n.description) bits.push(n.description); }));
    return bits.join(" | ").slice(0, 3500);
  }

  window.MonolithReceiveLaunchMode = mode => {
    if (!mode || mode === "home") return;
    setTimeout(() => {
      if (mode === "search") {
        window.JaneSceneRouter?.open?.("chat", {initialPortrait:true});
        $("#userInput")?.focus();
      } else if (mode === "assistant_context") {
        window.JaneSceneRouter?.open?.("chat", {initialPortrait:true});
        const input=$("#userInput"); const context=assistToPrompt();
        if(input && context) { input.value=`Use this current-screen context as working context, then answer my request: ${context}\n\n`; input.focus(); }
      } else if (mode === "voice") {
        window.JaneSceneRouter?.open?.("chat", {initialPortrait:true});
        try { window.AndroidJane?.startVoice?.(); } catch(_) {}
      } else if (mode === "image") {
        try { window.JaneOpenImage?.(); } catch(_) { window.JaneSceneRouter?.open?.("chat", {initialPortrait:true}); }
      }
    }, 350);
  };

  function installVaultBadge() {
    const head=$(".v79-kb-head"); if(!head || $(".monolith-vault-badge",head))return;
    const badge=document.createElement('div'); badge.className='monolith-vault-badge'; badge.innerHTML=`${I.archive}<span>KNOWLEDGE VAULT</span><small>VIEW // READ // DELETE // LOCAL MEMORY</small>`; head.appendChild(badge);
  }

  function refresh() {
    loadState(); rebrand(); installMenu(); installVaultBadge();
    if (state.overlay && !document.contains(state.overlay)) state.overlay=null;
    return true;
  }

  window.MonolithCore = { refresh, open, close };
  let tries=0; const timer=setInterval(()=>{tries++;refresh();if($("#janeMenuPanel")||tries>40)clearInterval(timer);},150);
  refresh();
})();
