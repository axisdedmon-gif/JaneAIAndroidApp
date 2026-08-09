(function () {
  "use strict";

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));
  const clamp = (value, min, max) => Math.max(min, Math.min(max, value));

  const ICONS = {
    chat: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5.5h16v10H9l-5 4z"/><path d="M8 9h8M8 12h5"/></svg>',
    archives: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 4h14v16H5z"/><path d="M8 4v16M11 8h5M11 11h5M11 14h4"/></svg>',
    studio: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3 8 4.5v9L12 21l-8-4.5v-9z"/><path d="m4 7.5 8 4.5 8-4.5M12 12v9"/></svg>',
    settings: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9.7 4.3 10.4 2h3.2l.7 2.3 2 .8 2.1-1.1 2.2 2.2-1.1 2.1.8 2 2.3.7v3.2l-2.3.7-.8 2 1.1 2.1-2.2 2.2-2.1-1.1-2 .8-.7 2.3h-3.2l-.7-2.3-2-.8-2.1 1.1-2.2-2.2 1.1-2.1-.8-2-2.3-.7V11l2.3-.7.8-2-1.1-2.1L5.6 4l2.1 1.1z"/><circle cx="12" cy="12.6" r="3.2"/></svg>',
    music: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 18V5l10-2v13"/><circle cx="6.5" cy="18" r="2.5"/><circle cx="16.5" cy="16" r="2.5"/><path d="M9 8l10-2"/></svg>',
    model: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m12 2.8 8 4.6v9.2l-8 4.6-8-4.6V7.4z"/><path d="m4 7.4 8 4.7 8-4.7M12 12.1v9.1"/><circle cx="12" cy="12" r="2.1"/></svg>',
    attach: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m8 12.5 6.9-6.9a3 3 0 1 1 4.2 4.2L10 18.9a5 5 0 0 1-7-7l9.4-9.4"/><path d="m6.2 14.7 8.5-8.5"/></svg>',
    mic: '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="8" y="3" width="8" height="12" rx="4"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3M8 21h8"/></svg>',
    image: '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8.5" cy="9" r="1.7"/><path d="m5 18 5-5 3 3 2-2 4 4"/></svg>',
    send: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m3 4 18 8-18 8 3-8zM6 12h15"/></svg>'
  };

  const state = {
    currentScene: "launch",
    soundEnabled: localStorage.getItem("jane.interface.sound") !== "off",
    archiveMotionEnabled: localStorage.getItem("jane.interface.motion") !== "off",
    portraitVariant: "Kadi_c",
    portraitPose: "",
    greetingMode: false,
    sceneMap: {},
    telemetryTimer: 0,
    clockTimer: 0
  };

  const audio = {
    context: null,
    master: null,
    ambientNodes: [],

    ensure() {
      if (!state.soundEnabled) return null;
      if (!this.context) {
        const AudioContextClass = window.AudioContext || window.webkitAudioContext;
        if (!AudioContextClass) return null;
        this.context = new AudioContextClass();
        this.master = this.context.createGain();
        this.master.gain.value = 0.42;
        this.master.connect(this.context.destination);
      }
      if (this.context.state === "suspended") this.context.resume().catch(() => {});
      return this.context;
    },

    tone(startHz, endHz, duration, volume, type, delay) {
      const ctx = this.ensure();
      if (!ctx || !this.master) return;
      const now = ctx.currentTime + (delay || 0);
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = type || "sine";
      osc.frequency.setValueAtTime(Math.max(1, startHz), now);
      osc.frequency.exponentialRampToValueAtTime(Math.max(1, endHz), now + duration);
      gain.gain.setValueAtTime(0.0001, now);
      gain.gain.exponentialRampToValueAtTime(Math.max(0.0002, volume), now + .012);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + duration);
      osc.connect(gain);
      gain.connect(this.master);
      osc.start(now);
      osc.stop(now + duration + .03);
    },

    noise(duration, volume, delay) {
      const ctx = this.ensure();
      if (!ctx || !this.master) return;
      const frames = Math.max(1, Math.floor(ctx.sampleRate * duration));
      const buffer = ctx.createBuffer(1, frames, ctx.sampleRate);
      const data = buffer.getChannelData(0);
      for (let i = 0; i < frames; i += 1) data[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / frames, 1.7);
      const source = ctx.createBufferSource();
      const gain = ctx.createGain();
      const filter = ctx.createBiquadFilter();
      const now = ctx.currentTime + (delay || 0);
      filter.type = "bandpass";
      filter.frequency.value = 2400;
      filter.Q.value = 1.2;
      gain.gain.setValueAtTime(volume, now);
      gain.gain.exponentialRampToValueAtTime(.0001, now + duration);
      source.buffer = buffer;
      source.connect(filter);
      filter.connect(gain);
      gain.connect(this.master);
      source.start(now);
    },

    cue(name) {
      if (!state.soundEnabled) return;
      if (name === "back") {
        this.tone(510, 190, .19, .055, "triangle", 0);
        this.tone(270, 150, .18, .026, "sine", .045);
      } else if (name === "launch") {
        this.tone(62, 125, .55, .06, "sine", 0);
        this.tone(170, 690, .42, .045, "sawtooth", .05);
        this.tone(690, 980, .28, .032, "triangle", .23);
        this.noise(.34, .028, .08);
      } else if (name === "forward") {
        this.tone(210, 540, .16, .045, "triangle", 0);
        this.tone(520, 790, .13, .022, "sine", .055);
        this.noise(.11, .014, 0);
      } else if (name === "confirm") {
        this.tone(360, 540, .11, .035, "sine", 0);
        this.tone(540, 720, .13, .024, "triangle", .07);
      } else if (name === "error") {
        this.tone(190, 115, .22, .045, "square", 0);
        this.tone(175, 95, .22, .025, "square", .12);
      } else {
        this.tone(390, 470, .075, .022, "triangle", 0);
        this.noise(.055, .007, 0);
      }
      try { if (navigator.vibrate) navigator.vibrate(name === "launch" ? [10, 22, 14] : 7); } catch (error) {}
    },

    stopAmbient() {
      const ctx = this.context;
      if (!ctx) return;
      this.ambientNodes.forEach(node => {
        try {
          if (node.gain) node.gain.exponentialRampToValueAtTime(.0001, ctx.currentTime + .18);
          if (node.stop) node.stop(ctx.currentTime + .22);
        } catch (error) {}
      });
      this.ambientNodes = [];
    },

    setAmbient(scene) {
      this.stopAmbient();
      const ctx = this.ensure();
      if (!ctx || !this.master || !state.soundEnabled || scene === "launch") return;
      const frequencies = {
        command: [43, 86], chat: [48, 96], archives: [54, 108],
        studio: [39, 117], music: [46, 138], model: [41, 123],
        settings: [45, 90], travel: [52, 104]
      }[scene] || [44, 88];
      const ambientGain = ctx.createGain();
      const filter = ctx.createBiquadFilter();
      const lfo = ctx.createOscillator();
      const lfoGain = ctx.createGain();
      ambientGain.gain.setValueAtTime(.0001, ctx.currentTime);
      ambientGain.gain.exponentialRampToValueAtTime(.010, ctx.currentTime + .35);
      filter.type = "lowpass";
      filter.frequency.value = 430;
      filter.Q.value = .7;
      frequencies.forEach((frequency, index) => {
        const osc = ctx.createOscillator();
        osc.type = index ? "triangle" : "sine";
        osc.frequency.value = frequency;
        osc.detune.value = index ? 3 : -3;
        osc.connect(filter);
        osc.start();
        this.ambientNodes.push(osc);
      });
      lfo.type = "sine";
      lfo.frequency.value = .13;
      lfoGain.gain.value = .0035;
      lfo.connect(lfoGain);
      lfoGain.connect(ambientGain.gain);
      lfo.start();
      filter.connect(ambientGain);
      ambientGain.connect(this.master);
      this.ambientNodes.push(lfo, ambientGain);
    },

    setEnabled(enabled) {
      state.soundEnabled = Boolean(enabled);
      localStorage.setItem("jane.interface.sound", state.soundEnabled ? "on" : "off");
      if (!state.soundEnabled) this.stopAmbient();
      else {
        this.ensure();
        this.cue("confirm");
        this.setAmbient(state.currentScene);
      }
      updateInterfaceSettings();
    }
  };

  const vfx = {
    scene: "launch",

    install() {
      const layer = document.createElement("div");
      layer.id = "janeFxLayer";
      layer.setAttribute("aria-hidden", "true");
      layer.innerHTML = '<div class="jane-scan-mesh"></div><div class="jane-noise"></div><div class="jane-vignette"></div>';
      document.body.appendChild(layer);
      const wipe = document.createElement("div");
      wipe.id = "janeSceneWipe";
      wipe.setAttribute("aria-hidden", "true");
      document.body.appendChild(wipe);
    },

    setScene(scene) { this.scene = scene; }
  };

  function makeSceneHeader(title, kicker, backId, backLabel) {
    const header = document.createElement("header");
    header.className = "jane-scene-header";
    header.innerHTML = `<button id="${backId}" class="jane-back-button" type="button">${backLabel || "Return"}</button><div><div class="jane-scene-title">${title}</div><div class="jane-scene-kicker" style="text-align:center;margin-top:3px">${kicker}</div></div><div class="jane-header-meta"><span class="jane-status-lamp"></span><span>CHANNEL STABLE</span></div>`;
    return header;
  }

  function navPod(id, icon, title, subtitle) {
    return `<button id="${id}" class="deck-nav-pod" type="button"><span class="deck-pod-icon">${icon}</span><span class="deck-pod-copy"><strong>${title}</strong><small>${subtitle}</small></span><span class="deck-pod-arrow" aria-hidden="true"></span></button>`;
  }

  function telemetryCell(id, label, gauge) {
    return `<article id="telemetry-${id}" class="telemetry-cell" data-gauge="${gauge ? "true" : "false"}"><div class="telemetry-label">${label}</div><div class="telemetry-value">LINKING</div><div class="telemetry-detail">DEVICE CHANNEL</div><div class="telemetry-track"><div class="telemetry-fill"></div></div></article>`;
  }

  function prepareCommandScene(home) {
    const stage = $(".home-stage", home);
    const prompt = $(".prompt-card", home);
    const history = $("#homeHistory", home);
    $("#janeVitalsHUD")?.remove();

    const topbar = document.createElement("header");
    topbar.className = "deck-topbar";
    topbar.innerHTML = '<div class="deck-identity"><strong>Jane</strong><span>CJ is allowing me to be accessed, so ask away.</span></div><div class="deck-core-title">Command Chamber</div><div class="deck-clock"><strong id="janeDeckClock">--:--:--</strong><span id="janeDeckDate">Temporal link</span></div>';

    const grid = document.createElement("div");
    grid.className = "deck-grid";

    const telemetry = document.createElement("aside");
    telemetry.id = "janeTelemetryPanel";
    telemetry.className = "deck-panel";
    telemetry.innerHTML = '<div class="telemetry-head"><strong>System Vitals</strong><span id="janeTelemetryState">LIVE</span></div><div id="janeTelemetryGrid">' +
      telemetryCell("core", "Core Integrity", true) + telemetryCell("vault", "Memory Vault", true) +
      telemetryCell("reserve", "Cognitive Reserve", true) + telemetryCell("cycle", "Active Cycle", false) +
      telemetryCell("focus", "Jane Focus", false) + telemetryCell("thermal", "Thermal Envelope", true) +
      telemetryCell("uplink", "Uplink Lattice", false) + telemetryCell("continuity", "System Continuity", false) +
      "</div>";

    const menu = document.createElement("nav");
    menu.id = "janeMenuPanel";
    menu.className = "deck-panel";
    menu.setAttribute("aria-label", "Jane destinations");
    menu.innerHTML = '<div class="menu-head"><strong>Destinations</strong><span>SELECT LINK</span></div><div class="deck-menu-list">' +
      navPod("janeNavChat", ICONS.chat, "Chat", "Expression dialogue channel") +
      navPod("janeNavArchives", ICONS.archives, "Archives", "Orbital knowledge chamber") +
      navPod("janeNavStudio", ICONS.studio, "Dedmon Studio", "Music and matter laboratory") +
      navPod("janeNavSettings", ICONS.settings, "Settings", "Private controls and links") +
      "</div>";

    const footer = document.createElement("footer");
    footer.className = "deck-footer";
    footer.innerHTML = '<span>LOCAL CORE // ACTIVE</span><span>JANE LINK // STABLE</span><span>TOUCH CONTROL // RIGHT PANEL ONLY</span>';

    if (stage) {
      const model = $("#homeJaneModel", stage);
      if (model) {
        model.removeAttribute("camera-controls");
        model.removeAttribute("disable-zoom");
        model.setAttribute("interaction-prompt", "none");
        model.setAttribute("camera-orbit", "0deg 78deg 112%");
        model.setAttribute("camera-target", "0m 0.52m 0m");
        model.setAttribute("field-of-view", "23deg");
        model.setAttribute("rotation-per-second", "9deg");
        model.setAttribute("auto-rotate-delay", "0");
        model.setAttribute("shadow-intensity", ".9");
        model.setAttribute("shadow-softness", ".78");
        model.setAttribute("exposure", "1.12");
      }
      if (!$(".jane-containment-ring", stage)) {
        const ring = document.createElement("div");
        ring.className = "jane-containment-ring";
        ring.setAttribute("aria-hidden", "true");
        stage.appendChild(ring);
      }
    }

    home.replaceChildren(topbar, grid, footer);
    grid.appendChild(telemetry);
    if (stage) grid.appendChild(stage);
    grid.appendChild(menu);
    return { prompt, history };
  }

  function replaceComposerIcons() {
    const replacements = [
      ["uploadBtn", ICONS.attach, "Attach knowledge, photo, or file"],
      ["micBtn", ICONS.mic, "Use microphone"],
      ["imageBtn", ICONS.image, "Image mode"],
      ["privateBtn", ICONS.settings, "Open Settings scene"],
      ["sendBtn", ICONS.send, "Send to Jane"]
    ];
    replacements.forEach(([id, icon, label]) => {
      const button = document.getElementById(id);
      if (!button) return;
      button.innerHTML = icon;
      button.title = label;
      button.setAttribute("aria-label", label);
    });
  }

  function prepareChatScene(vn, prompt, history) {
    const stage = $(".stage", vn);
    const dialog = $(".dialog", vn);
    const back = $("#backBtn", vn);
    const stop = $("#stopBtn", vn);
    const frame = document.createElement("div");
    frame.className = "jane-scene-frame";
    const header = document.createElement("header");
    header.className = "jane-scene-header";
    const center = document.createElement("div");
    center.innerHTML = '<div class="jane-scene-title">Chat</div><div class="jane-scene-kicker" style="text-align:center;margin-top:3px">Expression dialogue channel</div>';
    const right = document.createElement("div");
    right.className = "jane-header-meta";
    right.innerHTML = '<span class="jane-status-lamp"></span><span>VOICE / VISUAL LINK</span>';
    if (back) { back.classList.add("jane-back-button"); back.textContent = "Return"; header.appendChild(back); }
    else header.appendChild(document.createElement("span"));
    header.appendChild(center);
    if (stop) { stop.textContent = "Silence"; right.appendChild(stop); }
    header.appendChild(right);

    const grid = document.createElement("div");
    grid.className = "jane-chat-grid";
    const portraitBay = document.createElement("section");
    portraitBay.className = "jane-portrait-bay";
    const consolePanel = document.createElement("aside");
    consolePanel.className = "jane-chat-console";
    if (stage) portraitBay.appendChild(stage);
    const greetingSignal = document.createElement("div");
    greetingSignal.id = "janeGreetingSignal";
    greetingSignal.setAttribute("aria-hidden", "true");
    greetingSignal.innerHTML = '<svg viewBox="0 0 64 64"><path d="M23 34V17c0-4 6-4 6 0v13-17c0-4 6-4 6 0v17-14c0-4 6-4 6 0v16-10c0-4 6-4 6 0v18c0 10-7 17-17 17-8 0-12-5-16-11l-6-9c-2-4 3-7 6-4l9 8"/><path class="wave-arc" d="M11 12C6 17 4 23 5 29M17 7C10 12 7 19 8 27"/></svg><span>GREETING SIGNAL</span>';
    portraitBay.appendChild(greetingSignal);
    if (dialog) consolePanel.appendChild(dialog);
    if (history) consolePanel.appendChild(history);
    if (prompt) consolePanel.appendChild(prompt);
    grid.append(portraitBay, consolePanel);
    frame.append(header, grid);
    vn.replaceChildren(frame);

    const input = $("#userInput", vn);
    if (input && input.value.trim() === "Tell me a riddle.") input.value = "";
    replaceComposerIcons();
  }

  function prepareArchivesScene(archives) {
    const heading = $(".v79-kb-head h2", archives);
    const subtitle = $(".v79-kb-head p", archives);
    const close = $("#v79CloseKnowledgeBtn", archives);
    if (heading) heading.textContent = "Archives";
    if (subtitle) subtitle.textContent = "Jane's preserved native intelligence library.";
    if (close) { close.textContent = "Return"; close.classList.add("jane-back-button"); }
    const browser = $("#v79ArchiveBrowser", archives);
    if (browser && !$("#janeArchiveCore", browser)) {
      const core = document.createElement("div");
      core.id = "janeArchiveCore";
      core.setAttribute("aria-hidden", "true");
      const spin = document.createElement("div");
      spin.id = "janeArchiveSpin";
      spin.innerHTML = 'Orbital inertia<strong id="janeArchiveSpinValue">0.12 rad/s</strong>';
      browser.append(core, spin);
    }
  }

  function prepareStudioHub() {
    const hub = document.createElement("section");
    hub.id = "janeStudioHub";
    hub.setAttribute("aria-hidden", "true");
    const frame = document.createElement("div");
    frame.className = "jane-scene-frame";
    frame.appendChild(makeSceneHeader("Dedmon Studio", "Virtual creation laboratory", "janeStudioHubBack", "Return"));
    const lab = document.createElement("div");
    lab.className = "studio-lab";
    lab.innerHTML = `<button id="janeOpenMusicLab" class="lab-console" type="button">${ICONS.music}<strong>Sonic Forge</strong><span>Cover creation, genre reconstruction, extension, variation, preview and export systems.</span><em>Open audio laboratory</em></button><div class="lab-reactor" aria-hidden="true"></div><button id="janeOpenMatterLab" class="lab-console" type="button">${ICONS.model}<strong>Matter Forge</strong><span>Text-to-model, image reconstruction, rigging, live inspection and multi-format export systems.</span><em>Open 3D laboratory</em></button>`;
    frame.appendChild(lab);
    hub.appendChild(frame);
    return hub;
  }

  function prepareStudioScenes(meshy, music) {
    const meshyTitle = $(".v61Head h2", meshy);
    const meshyClose = $("#janeCloseMeshyStudio", meshy);
    if (meshyTitle) meshyTitle.textContent = "Matter Forge // 3D Laboratory";
    if (meshyClose) { meshyClose.textContent = "Studio Hub"; meshyClose.classList.add("jane-back-button"); }
    const musicTitle = $(".v65Head h2", music);
    const musicClose = $("#closeMusicStudioBtn", music);
    if (musicTitle) musicTitle.textContent = "Sonic Forge // Audio Laboratory";
    if (musicClose) { musicClose.textContent = "Studio Hub"; musicClose.classList.add("jane-back-button"); }
  }

  function prepareSettingsScene(settings) {
    const heading = $(".settings-head h2", settings);
    const close = $("#closeSettingsBtn", settings);
    const card = $(".settings-card", settings);
    if (heading) heading.textContent = "Settings // Private Systems";
    if (close) { close.textContent = "Return"; close.classList.add("jane-back-button"); }
    if (!card || $("#janeTelemetryPermission", card)) return;

    const permission = document.createElement("section");
    permission.id = "janeTelemetryPermission";
    permission.className = "settings-section";
    permission.innerHTML = '<div class="permission-copy"><strong>Device telemetry link</strong><span>Battery, storage, memory, temperature, network, and uptime are read locally without runtime permission. <span id="janeUsageAccessState">Usage Access is checking…</span></span></div><button id="janeUsageAccessButton" type="button">Open Usage Access</button>';

    const interfaceSettings = document.createElement("section");
    interfaceSettings.id = "janeInterfaceSettings";
    interfaceSettings.className = "settings-section";
    interfaceSettings.innerHTML = '<div class="jane-setting-chip"><span>Interface FX</span><button id="janeSoundToggle" type="button">Sound: On</button></div><div class="jane-setting-chip"><span>Archive motion</span><button id="janeMotionToggle" type="button">Motion: On</button></div><div class="jane-setting-chip"><span>Navigation relay</span><button id="janeOpenTravelScene" type="button">Open Travel</button></div>';

    const insertionPoint = $("#ownerSetup", card) || card.children[1] || null;
    card.insertBefore(permission, insertionPoint);
    card.insertBefore(interfaceSettings, insertionPoint);

    const nexus = document.createElement("section");
    nexus.id = "janeSettingsNexus";
    nexus.setAttribute("aria-hidden", "true");
    nexus.innerHTML = '<div class="settings-nexus-orbit orbit-one"></div><div class="settings-nexus-orbit orbit-two"></div><div class="settings-nexus-core"><span>LOCAL</span><strong>PRIVATE NEXUS</strong><em>SEALED</em></div><div class="settings-nexus-node node-one"><span></span><strong>MEMORY VAULT</strong><small>OWNER BOUND</small></div><div class="settings-nexus-node node-two"><span></span><strong>PERSONALITY CORE</strong><small>UNCHANGED</small></div><div class="settings-nexus-node node-three"><span></span><strong>ARCHIVE LINK</strong><small>NATIVE STORAGE</small></div>';
    card.appendChild(nexus);
  }

  function prepareTravelScene(travel) {
    const heading = $(".settings-head h2", travel);
    const close = $("#closeTravelBtn", travel);
    if (heading) heading.textContent = "Navigation Relay // Travel";
    if (close) { close.textContent = "Settings"; close.classList.add("jane-back-button"); }
  }

  const portraitFiles = {
    approve: "avatar_approve_thumbs_up.webp",
    confused: "avatar_confused_shrug.webp",
    defiant: "avatar_defiant_middle_finger.webp",
    rockon: "avatar_dio_rock_on.webp",
    excited: "avatar_excited_wave.webp",
    wave: "avatar_excited_wave.webp",
    smile: "avatar_happy_smile.webp",
    happy: "avatar_happy_smile.webp",
    clasped: "avatar_idle_clasped_hands.webp",
    collarbone: "avatar_idle_collarbone_touch.webp",
    forearm: "avatar_idle_forearm_hold.webp",
    idle: "avatar_neutral_idle.webp",
    neutral: "avatar_neutral_idle.webp",
    speaking: "avatar_idle_forearm_hold.webp",
    behindback: "avatar_idle_hands_behind_back.webp",
    irritated: "avatar_irritated_idle.webp",
    laughing: "avatar_laughing_idle.webp",
    playful: "avatar_playful_peace_wink.webp",
    relaxed: "avatar_relaxed_arms_behind_head.webp",
    sad: "avatar_sad_idle.webp",
    sarcastic: "avatar_sarcastic_arms_crossed.webp",
    seductive: "avatar_seductive_lip_bite.webp",
    shocked: "avatar_shocked_hand_mouth.webp",
    stop: "avatar_stop_wait_palm.webp",
    thinking: "avatar_thinking_chin.webp",
    triumphant: "avatar_triumphant_fist_pump.webp"
  };

  function portraitPath(pose) {
    const key = String(pose || "neutral").toLowerCase();
    const safeGreeting = state.greetingMode && (key === "wave" || key === "excited") && state.portraitVariant !== "Kadi_a";
    const file = safeGreeting ? portraitFiles.smile : (portraitFiles[key] || portraitFiles.neutral);
    return `dialog_portraits_webp/${state.portraitVariant}/01_static/${file}`;
  }

  function applyPortrait(pose, force) {
    const image = document.getElementById("janePose");
    if (!image) return;
    const resolved = String(pose || image.dataset.pose || "neutral").toLowerCase();
    const greetingActive = state.greetingMode && (resolved === "wave" || resolved === "excited");
    if (!greetingActive) state.greetingMode = false;
    const portraitBay = image.closest(".jane-portrait-bay");
    if (portraitBay) portraitBay.dataset.janeGreeting = greetingActive ? "true" : "false";
    if (!force && resolved === state.portraitPose && String(image.src).includes(`/${state.portraitVariant}/`)) return;
    state.portraitPose = resolved;
    image.dataset.pose = resolved;
    image.src = portraitPath(resolved);
    image.dataset.janePortrait = `command-deck-${state.portraitVariant}`;
  }

  function enterChatDefault() {
    const variants = ["Kadi_a", "Kadi_b", "Kadi_c"];
    state.portraitVariant = variants[Math.floor(Math.random() * variants.length)];
    state.portraitPose = "";
    state.greetingMode = true;
    applyPortrait("wave", true);
    const dialog = document.getElementById("dialogText");
    if (dialog && (!dialog.textContent.trim() || dialog.textContent.trim() === "...")) dialog.textContent = "Dialogue channel ready.";
  }

  function installPortraitGuard() {
    const image = document.getElementById("janePose");
    const dialog = document.getElementById("dialogText");
    if (!image) return;
    const sync = () => {
      if (state.currentScene !== "chat") return;
      const pose = image.dataset.pose || state.portraitPose || "neutral";
      requestAnimationFrame(() => applyPortrait(pose, false));
    };
    if (dialog) new MutationObserver(sync).observe(dialog, { childList: true, subtree: true, characterData: true });
    window.setInterval(sync, 260);
  }

  function formatBytes(bytes) {
    const value = Number(bytes || 0);
    if (!value) return "0 GB";
    const gb = value / 1073741824;
    if (gb >= 1) return `${gb >= 10 ? gb.toFixed(1) : gb.toFixed(2)} GB`;
    return `${(value / 1048576).toFixed(0)} MB`;
  }

  function formatDuration(milliseconds) {
    const totalMinutes = Math.max(0, Math.round(Number(milliseconds || 0) / 60000));
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    if (hours >= 24) return `${Math.floor(hours / 24)}d ${hours % 24}h`;
    return hours ? `${hours}h ${minutes}m` : `${minutes}m`;
  }

  function setTelemetry(id, value, detail, percent, tone) {
    const cell = document.getElementById(`telemetry-${id}`);
    if (!cell) return;
    $(".telemetry-value", cell).textContent = value;
    $(".telemetry-detail", cell).textContent = detail;
    cell.style.setProperty("--telemetry", `${clamp(Number(percent || 0), 0, 100)}%`);
    cell.dataset.tone = tone || "ice";
  }

  function unavailableTelemetry() {
    ["core", "vault", "reserve", "cycle", "focus", "thermal", "uplink", "continuity"].forEach(id => setTelemetry(id, "NO LINK", "DEVICE BRIDGE OFFLINE", 0, "alert"));
    const status = document.getElementById("janeTelemetryState");
    if (status) status.textContent = "OFFLINE";
  }

  function updateTelemetry() {
    if (!window.AndroidJane || typeof window.AndroidJane.getDeviceVitals !== "function") {
      unavailableTelemetry();
      updateUsageAccessState(false, true);
      return;
    }
    try {
      const data = JSON.parse(window.AndroidJane.getDeviceVitals() || "{}");
      const battery = Number(data.batteryPercent);
      const batteryTone = battery >= 55 ? "mint" : battery >= 25 ? "amber" : "alert";
      setTelemetry("core", battery >= 0 ? `${battery}%` : "UNKNOWN", `${String(data.batteryHealth || "unverified").replace(/-/g," ")} · ${String(data.batteryStatus || "unknown")}`, battery >= 0 ? battery : 0, batteryTone);
      const storageUsed = Number(data.storageUsedPercent || 0);
      setTelemetry("vault", `${storageUsed.toFixed(1)}%`, `${formatBytes(data.storageUsedBytes)} / ${formatBytes(data.storageTotalBytes)}`, storageUsed, storageUsed > 88 ? "alert" : storageUsed > 72 ? "amber" : "ice");
      const memoryUsed = Number(data.memoryUsedPercent || 0);
      setTelemetry("reserve", `${Math.max(0, 100 - memoryUsed).toFixed(0)}%`, `${formatBytes(data.memoryAvailableBytes)} AVAILABLE`, 100 - memoryUsed, data.memoryPressure ? "alert" : "mint");
      const usage = data.usage || {};
      if (usage.granted) {
        setTelemetry("cycle", formatDuration(usage.screenTimeMs), "SCREEN ACTIVE TODAY", 0, "ice");
        setTelemetry("focus", formatDuration(usage.janeTimeMs), "JANE FOREGROUND TODAY", 0, "ice");
      } else {
        setTelemetry("cycle", "LINK NEEDED", "ENABLE IN SETTINGS", 0, "amber");
        setTelemetry("focus", "LINK NEEDED", "ENABLE IN SETTINGS", 0, "amber");
      }
      const temperature = Number(data.batteryTemperatureC || 0);
      const thermalState = String(data.thermalState || "unverified").replace(/-/g," ");
      const thermalTone = /critical|emergency|shutdown|restricted/.test(thermalState) ? "alert" : /warm|elevated/.test(thermalState) ? "amber" : "mint";
      setTelemetry("thermal", thermalState.toUpperCase(), `${temperature ? temperature.toFixed(1) + "°C" : "TEMP UNAVAILABLE"}${data.powerReserve ? " · RESERVE" : ""}`, temperature ? temperature / 60 * 100 : 0, thermalTone);
      setTelemetry("uplink", data.linkConnected ? String(data.linkType || "linked").toUpperCase() : "OFFLINE", data.linkConnected ? "NETWORK CHANNEL ACTIVE" : "LOCAL SYSTEMS ONLY", 0, data.linkConnected ? "mint" : "amber");
      setTelemetry("continuity", formatDuration(data.uptimeMs), "DEVICE UPTIME", 0, "ice");
      const status = document.getElementById("janeTelemetryState");
      if (status) status.textContent = "LIVE";
      updateUsageAccessState(Boolean(usage.granted), false);
    } catch (error) {
      unavailableTelemetry();
      console.warn("[Jane telemetry]", error);
    }
  }

  function updateUsageAccessState(granted, bridgeUnavailable) {
    const label = document.getElementById("janeUsageAccessState");
    const button = document.getElementById("janeUsageAccessButton");
    if (label) label.textContent = bridgeUnavailable ? "Device bridge is unavailable in browser preview." : granted ? "Usage Access is linked; daily screen and Jane focus time are live." : "Usage Access is not linked; daily screen and app time remain hidden.";
    if (button) {
      button.textContent = granted ? "Usage Access Linked" : "Open Usage Access";
      button.disabled = Boolean(granted);
    }
  }

  function updateClock() {
    const now = new Date();
    const clock = document.getElementById("janeDeckClock");
    const date = document.getElementById("janeDeckDate");
    if (clock) clock.textContent = now.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
    if (date) date.textContent = now.toLocaleDateString([], { weekday: "short", month: "short", day: "numeric" });
  }

  function updateInterfaceSettings() {
    const sound = document.getElementById("janeSoundToggle");
    const motion = document.getElementById("janeMotionToggle");
    if (sound) sound.textContent = `Sound: ${state.soundEnabled ? "On" : "Off"}`;
    if (motion) motion.textContent = `Motion: ${state.archiveMotionEnabled ? "On" : "Off"}`;
  }

  const orbit = {
    angle: 0,
    velocity: 0,
    dragging: false,
    lastX: 0,
    moved: 0,
    lastTime: 0,
    list: null,
    observer: null,

    install() {
      this.list = document.getElementById("v79ArchiveList");
      if (!this.list) return;
      this.list.addEventListener("pointerdown", event => {
        this.dragging = true;
        this.lastX = event.clientX;
        this.moved = 0;
        this.velocity = 0;
        try { this.list.setPointerCapture(event.pointerId); } catch (error) {}
      });
      this.list.addEventListener("pointermove", event => {
        if (!this.dragging) return;
        const dx = event.clientX - this.lastX;
        this.lastX = event.clientX;
        this.moved += Math.abs(dx);
        this.angle += dx * .0048;
        this.velocity = dx * .00135;
        this.layout();
        event.preventDefault();
      }, { passive: false });
      const release = event => {
        if (!this.dragging) return;
        this.dragging = false;
        try { this.list.releasePointerCapture(event.pointerId); } catch (error) {}
      };
      this.list.addEventListener("pointerup", release);
      this.list.addEventListener("pointercancel", release);
      this.list.addEventListener("click", event => {
        if (this.moved > 9) {
          event.preventDefault();
          event.stopImmediatePropagation();
          this.moved = 0;
        }
      }, true);
      this.observer = new MutationObserver(() => requestAnimationFrame(() => this.layout()));
      this.observer.observe(this.list, { childList: true });
      window.addEventListener("resize", () => this.layout(), { passive: true });
      requestAnimationFrame(time => this.tick(time));
    },

    cards() { return $$(".v79-archive-card", this.list); },

    layout() {
      if (!this.list) return;
      const cards = this.cards();
      if (!cards.length) return;
      const rect = this.list.getBoundingClientRect();
      const radiusX = Math.min(rect.width * .34, 330);
      const radiusY = Math.min(rect.height * .22, 105);
      cards.forEach((card, index) => {
        const phase = this.angle + index * Math.PI * 2 / cards.length;
        const depth = (Math.cos(phase) + 1) / 2;
        const x = Math.sin(phase) * radiusX;
        const y = Math.cos(phase) * radiusY;
        const scale = .56 + depth * .44;
        const opacity = .18 + depth * .82;
        card.style.setProperty("--orbit-x", `${x.toFixed(2)}px`);
        card.style.setProperty("--orbit-y", `${y.toFixed(2)}px`);
        card.style.setProperty("--orbit-scale", scale.toFixed(3));
        card.style.setProperty("--orbit-opacity", opacity.toFixed(3));
        card.style.setProperty("--orbit-front", depth.toFixed(3));
        card.style.setProperty("--orbit-tilt", `${(-Math.sin(phase) * 13).toFixed(2)}deg`);
        card.style.setProperty("--orbit-blur", `${((1 - depth) * 1.15).toFixed(2)}px`);
        card.style.setProperty("--orbit-z", String(Math.round(depth * 100) + 1));
        card.setAttribute("aria-hidden", depth < .16 ? "true" : "false");
      });
    },

    tick(time) {
      requestAnimationFrame(next => this.tick(next));
      const dt = clamp(time - (this.lastTime || time), 0, 40);
      this.lastTime = time;
      if (state.currentScene !== "archives" || !state.archiveMotionEnabled || this.dragging) return;
      this.angle += .000075 * dt + this.velocity;
      this.velocity *= Math.pow(.92, dt / 16.67);
      if (Math.abs(this.velocity) < .000025) this.velocity = 0;
      this.layout();
      const value = document.getElementById("janeArchiveSpinValue");
      if (value) value.textContent = `${Math.abs((this.velocity + .000075 * dt) * 60).toFixed(2)} rad/s`;
    }
  };

  function legacyClassSync(name) {
    const home = state.sceneMap.command;
    const chat = state.sceneMap.chat;
    const archives = state.sceneMap.archives;
    const settings = state.sceneMap.settings;
    const travel = state.sceneMap.travel;
    const model = state.sceneMap.model;
    const music = state.sceneMap.music;
    if (home) home.classList.toggle("hidden", name !== "command");
    if (chat) chat.classList.toggle("active", name === "chat");
    if (archives) archives.classList.toggle("open", name === "archives");
    if (settings) settings.classList.toggle("active", name === "settings");
    if (travel) travel.classList.toggle("active", name === "travel");
    if (model) model.classList.toggle("open", name === "model");
    if (music) music.classList.toggle("open", name === "music");
    document.body.classList.toggle("v79-knowledge-open", name === "archives");
    document.body.classList.remove("jane-overlay-open", "jane-studio-open", "jane-settings-open");
  }

  function routeHash(name) {
    return { command: "command", chat: "chat", archives: "archives", studio: "studio", music: "sonic-forge", model: "matter-forge", settings: "settings", travel: "navigation-relay" }[name] || "command";
  }

  function activateScene(name, options) {
    const opts = Object.assign({ push: true, replace: false, cue: "forward", initialPortrait: false }, options || {});
    const target = state.sceneMap[name];
    if (!target) return;
    const changed = state.currentScene !== name;
    Object.entries(state.sceneMap).forEach(([sceneName, element]) => {
      if (!element) return;
      const active = sceneName === name;
      element.dataset.janeActive = active ? "true" : "false";
      element.setAttribute("aria-hidden", active ? "false" : "true");
    });
    legacyClassSync(name);
    state.currentScene = name;
    document.body.dataset.janeScene = name;
    vfx.setScene(name);
    if (changed && opts.cue) audio.cue(opts.cue);
    if (changed) audio.setAmbient(name);

    const wipe = document.getElementById("janeSceneWipe");
    if (changed && wipe) {
      wipe.classList.remove("active");
      void wipe.offsetWidth;
      wipe.classList.add("active");
    }

    if (opts.push || opts.replace) {
      const historyState = { janeScene: name };
      const url = `#${routeHash(name)}`;
      try {
        if (opts.replace) history.replaceState(historyState, "", url);
        else history.pushState(historyState, "", url);
      } catch (error) {}
    }

    if (name === "chat" && opts.initialPortrait) enterChatDefault();
    if (name === "archives") {
      orbit.layout();
      try { window.JaneNativeArchiveCatalogChanged?.(); } catch (error) {}
    }
    if (name === "command" || name === "settings") updateTelemetry();
    if (name === "settings") {
      try { if (typeof window.refreshSettingsVisibility === "function") window.refreshSettingsVisibility(false); } catch (error) {}
      updateInterfaceSettings();
    }
    if (name === "travel") {
      try {
        if (typeof window.initMap === "function") window.initMap();
        if (typeof window.updateTravelStatus === "function") window.updateTravelStatus();
        if (typeof window.updateHomeStatus === "function") window.updateHomeStatus();
        if (typeof window.startLiveGps === "function") window.startLiveGps();
        setTimeout(() => { try { if (window.travelMap) window.travelMap.invalidateSize(); } catch (error) {} }, 220);
      } catch (error) { console.warn("[Jane travel scene]", error); }
    }
  }

  function navigateBack(fallback) {
    audio.cue("back");
    const currentHistoryScene = history.state && history.state.janeScene;
    if (currentHistoryScene === state.currentScene && history.length > 1) {
      history.back();
    } else {
      activateScene(fallback || "command", { push: false, replace: true, cue: null });
    }
  }

  function captureRoute(button, handler) {
    if (!button) return;
    button.addEventListener("click", event => {
      event.preventDefault();
      event.stopImmediatePropagation();
      handler(event);
    }, true);
  }

  function installRouting() {
    captureRoute(document.getElementById("janeNavChat"), () => activateScene("chat", { initialPortrait: true }));
    captureRoute(document.getElementById("janeNavArchives"), () => activateScene("archives"));
    captureRoute(document.getElementById("janeNavStudio"), () => activateScene("studio"));
    captureRoute(document.getElementById("janeNavSettings"), () => activateScene("settings"));
    captureRoute(document.getElementById("janeStudioHubBack"), () => navigateBack("command"));
    captureRoute(document.getElementById("janeOpenMusicLab"), () => activateScene("music"));
    captureRoute(document.getElementById("janeOpenMatterLab"), () => activateScene("model"));
    captureRoute(document.getElementById("backBtn"), () => navigateBack("command"));
    captureRoute(document.getElementById("dialogCloseBtn"), () => {
      try { if (typeof window.setDialogReplyComposer === "function") window.setDialogReplyComposer(false); } catch (error) {}
      navigateBack("command");
    });
    captureRoute(document.getElementById("v79CloseKnowledgeBtn"), () => navigateBack("command"));
    captureRoute(document.getElementById("janeCloseMeshyStudio"), () => navigateBack("studio"));
    captureRoute(document.getElementById("closeMusicStudioBtn"), () => navigateBack("studio"));
    captureRoute(document.getElementById("closeSettingsBtn"), () => navigateBack("command"));
    captureRoute(document.getElementById("closeTravelBtn"), () => navigateBack("settings"));
    captureRoute(document.getElementById("privateBtn"), () => activateScene("settings"));
    captureRoute(document.getElementById("janeOpenTravelScene"), () => activateScene("travel"));

    const launch = document.getElementById("launchJaneButton");
    if (launch) {
      launch.addEventListener("click", () => {
        audio.ensure();
        audio.cue("launch");
        setTimeout(() => activateScene("command", { push: false, replace: true, cue: null }), 210);
      }, true);
    }

    const usageButton = document.getElementById("janeUsageAccessButton");
    if (usageButton) usageButton.addEventListener("click", () => {
      audio.cue("confirm");
      try { window.AndroidJane?.openUsageAccessSettings?.(); }
      catch (error) { audio.cue("error"); }
    });

    document.getElementById("janeSoundToggle")?.addEventListener("click", () => audio.setEnabled(!state.soundEnabled));
    document.getElementById("janeMotionToggle")?.addEventListener("click", () => {
      state.archiveMotionEnabled = !state.archiveMotionEnabled;
      localStorage.setItem("jane.interface.motion", state.archiveMotionEnabled ? "on" : "off");
      audio.cue("confirm");
      updateInterfaceSettings();
    });

    document.getElementById("stopBtn")?.addEventListener("click", () => audio.cue("tap"), true);
    document.getElementById("dialogReplyBtn")?.addEventListener("click", () => audio.cue("tap"), true);
    document.getElementById("v79ReaderBack")?.addEventListener("click", () => audio.cue("back"), true);
    document.getElementById("v79ImportBtn")?.addEventListener("click", () => audio.cue("confirm"), true);

    window.addEventListener("popstate", event => {
      const destination = event.state && event.state.janeScene;
      activateScene(state.sceneMap[destination] ? destination : "command", { push: false, cue: null });
      audio.setAmbient(state.currentScene);
    });

    document.addEventListener("click", event => {
      const button = event.target.closest && event.target.closest("button");
      if (!button || button.disabled || button.closest("[aria-hidden='true']")) return;
      const dedicatedCueButtons = new Set([
        "launchJaneButton",
        "janeUsageAccessButton",
        "janeSoundToggle",
        "janeMotionToggle",
        "stopBtn",
        "dialogReplyBtn",
        "v79ReaderBack",
        "v79ImportBtn"
      ]);
      if (dedicatedCueButtons.has(button.id)) return;
      audio.cue("tap");
    });
  }

  function wrapLegacyDialogs() {
    const originalShow = window.showDialog;
    if (typeof originalShow === "function") {
      window.showDialog = function () {
        const result = originalShow.apply(this, arguments);
        activateScene("chat", { push: state.currentScene !== "chat", cue: state.currentScene !== "chat" ? "forward" : null, initialPortrait: false });
        setTimeout(() => applyPortrait(document.getElementById("janePose")?.dataset.pose || "speaking", true), 155);
        return result;
      };
    }
    const originalHide = window.hideDialog;
    if (typeof originalHide === "function") {
      window.hideDialog = function () {
        const result = originalHide.apply(this, arguments);
        activateScene("command", { push: false, replace: true, cue: null });
        return result;
      };
    }
  }

  function build() {
    if (document.body.classList.contains("jane-deck-ready")) return;
    const home = document.getElementById("home");
    const chat = document.getElementById("vn");
    const launch = document.getElementById("ownerGate");
    const archives = document.getElementById("janeKnowledgeBase");
    const meshy = document.getElementById("janeMeshyStudio");
    const music = document.getElementById("janeMusicStudio");
    const settings = document.getElementById("ownerModal");
    const travel = document.getElementById("travelModal");
    if (!home || !chat || !launch || !archives || !meshy || !music || !settings || !travel) {
      console.error("[Jane Command Deck] Required legacy scene missing.");
      return;
    }

    vfx.install();
    const relocated = prepareCommandScene(home);
    prepareChatScene(chat, relocated.prompt, relocated.history);
    prepareArchivesScene(archives);
    const studio = prepareStudioHub();
    prepareStudioScenes(meshy, music);
    prepareSettingsScene(settings);
    prepareTravelScene(travel);

    const host = document.createElement("main");
    host.id = "janeSceneHost";
    document.body.appendChild(host);
    const scenes = { launch, command: home, chat, archives, studio, music, model: meshy, settings, travel };
    Object.entries(scenes).forEach(([name, element]) => {
      element.dataset.janeScene = name;
      element.dataset.janeActive = "false";
      element.setAttribute("aria-hidden", "true");
      host.appendChild(element);
    });
    state.sceneMap = scenes;
    document.body.classList.add("jane-deck-ready");

    orbit.install();
    installPortraitGuard();
    installRouting();
    wrapLegacyDialogs();
    updateInterfaceSettings();
    updateClock();
    state.clockTimer = window.setInterval(updateClock, 1000);
    updateTelemetry();
    state.telemetryTimer = window.setInterval(updateTelemetry, 15000);
    window.JaneDeviceVitalsChanged = updateTelemetry;
    window.JaneDeviceVitalsError = message => console.warn("[Jane telemetry]", message);
    window.JaneSceneRouter = { open: activateScene, back: navigateBack, current: () => state.currentScene };

    const launchVisible = !launch.classList.contains("hidden");
    activateScene(launchVisible ? "launch" : "command", { push: false, replace: !launchVisible, cue: null });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", build, { once: true });
  else build();
})();
