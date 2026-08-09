(function () {
  "use strict";

  if (window.JaneQolHud && window.JaneQolHud.version) {
    window.JaneQolHud.refresh();
    return;
  }

  const VERSION = "QOL-HUD-1.0";
  const CUT = "polygon(0 0, 100% 0, 100% calc(100% - 15px), calc(100% - 15px) 100%, 0 100%)";
  const $ = (selector, root = document) => root.querySelector(selector);
  const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
  const lerp = (a, b, speed) => a + (b - a) * speed;

  /* Lucide icon subset, ISC licensed. All HUD icons use a 1.25px stroke. */
  const LUCIDE = {
    messageSquare: [["path", { d: "M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4z" }]],
    database: [
      ["ellipse", { cx: "12", cy: "5", rx: "9", ry: "3" }],
      ["path", { d: "M3 5v14c0 1.7 4 3 9 3s9-1.3 9-3V5" }],
      ["path", { d: "M3 12c0 1.7 4 3 9 3s9-1.3 9-3" }]
    ],
    box: [
      ["path", { d: "M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" }],
      ["path", { d: "m3.3 7 8.7 5 8.7-5" }],
      ["path", { d: "M12 22V12" }]
    ],
    sliders: [
      ["line", { x1: "4", y1: "21", x2: "4", y2: "14" }],
      ["line", { x1: "4", y1: "10", x2: "4", y2: "3" }],
      ["line", { x1: "12", y1: "21", x2: "12", y2: "12" }],
      ["line", { x1: "12", y1: "8", x2: "12", y2: "3" }],
      ["line", { x1: "20", y1: "21", x2: "20", y2: "16" }],
      ["line", { x1: "20", y1: "12", x2: "20", y2: "3" }],
      ["line", { x1: "1", y1: "14", x2: "7", y2: "14" }],
      ["line", { x1: "9", y1: "8", x2: "15", y2: "8" }],
      ["line", { x1: "17", y1: "16", x2: "23", y2: "16" }]
    ],
    audioLines: [
      ["path", { d: "M2 10v3" }], ["path", { d: "M6 6v11" }],
      ["path", { d: "M10 3v18" }], ["path", { d: "M14 8v7" }],
      ["path", { d: "M18 5v13" }], ["path", { d: "M22 10v3" }]
    ],
    hexagon: [["path", { d: "M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" }]],
    paperclip: [["path", { d: "m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" }]],
    mic: [
      ["path", { d: "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z" }],
      ["path", { d: "M19 10v2a7 7 0 0 1-14 0v-2" }],
      ["line", { x1: "12", y1: "19", x2: "12", y2: "22" }]
    ],
    scan: [
      ["path", { d: "M3 7V5a2 2 0 0 1 2-2h2" }],
      ["path", { d: "M17 3h2a2 2 0 0 1 2 2v2" }],
      ["path", { d: "M21 17v2a2 2 0 0 1-2 2h-2" }],
      ["path", { d: "M7 21H5a2 2 0 0 1-2-2v-2" }],
      ["line", { x1: "7", y1: "12", x2: "17", y2: "12" }]
    ],
    send: [
      ["path", { d: "m22 2-7 20-4-9-9-4Z" }],
      ["path", { d: "M22 2 11 13" }]
    ],
    radio: [
      ["circle", { cx: "12", cy: "12", r: "2" }],
      ["path", { d: "M16.24 7.76a6 6 0 0 1 0 8.49" }],
      ["path", { d: "M7.76 16.24a6 6 0 0 1 0-8.49" }],
      ["path", { d: "M19.07 4.93a10 10 0 0 1 0 14.14" }],
      ["path", { d: "M4.93 19.07a10 10 0 0 1 0-14.14" }]
    ],
    arrowLeft: [["path", { d: "M19 12H5" }], ["path", { d: "m12 19-7-7 7-7" }]],
    activity: [["path", { d: "M22 12h-4l-3 9L9 3l-3 9H2" }]],
    thermometer: [
      ["path", { d: "M14 4v10.54a4 4 0 1 1-4 0V4a2 2 0 0 1 4 0Z" }],
      ["line", { x1: "12", y1: "9", x2: "12", y2: "17" }]
    ],
    clock: [["circle", { cx: "12", cy: "12", r: "9" }], ["path", { d: "M12 7v5l3 2" }]],
    zap: [["path", { d: "M13 2 3 14h9l-1 8 10-12h-9z" }]]
  };

  function icon(name, className) {
    const nodes = LUCIDE[name] || LUCIDE.hexagon;
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("fill", "none");
    svg.setAttribute("stroke", "currentColor");
    svg.setAttribute("stroke-width", "1.25");
    svg.setAttribute("stroke-linecap", "square");
    svg.setAttribute("stroke-linejoin", "miter");
    svg.setAttribute("aria-hidden", "true");
    svg.classList.add("lucide-hud");
    if (className) svg.classList.add(className);
    nodes.forEach(([tag, attrs]) => {
      const node = document.createElementNS("http://www.w3.org/2000/svg", tag);
      Object.entries(attrs).forEach(([key, value]) => node.setAttribute(key, value));
      svg.appendChild(node);
    });
    return svg;
  }

  const iconMap = {
    janeNavChat: "messageSquare",
    janeNavArchives: "database",
    janeNavStudio: "box",
    janeNavSettings: "sliders",
    uploadBtn: "paperclip",
    micBtn: "mic",
    imageBtn: "scan",
    privateBtn: "sliders",
    sendBtn: "send"
  };

  const telemetry = {
    target: { core: 0, density: 0, headroom: 0, load: 0, thermal: 0, signal: 0, flux: 0, reserveUsed: 0 },
    shown: { core: 0, density: 0, headroom: 0, load: 0, thermal: 0, signal: 0, flux: 0, reserveUsed: 0 },
    raw: null,
    samples: [],
    poll: 0,
    frame: 0,
    installed: false
  };

  function segmentStrip(count, className) {
    return `<div class="hud-segments ${className || ""}" aria-hidden="true">${Array.from({ length: count }, (_, i) => `<i data-seg="${i}"></i>`).join("")}</div>`;
  }

  function bracketLayer() {
    return '<span class="hud-bracket-layer" aria-hidden="true"></span>';
  }

  function cell(id, label, iconName, meter) {
    return `<article id="qol-${id}" class="qol-telemetry-cell" data-tone="ice">${bracketLayer()}<header><span class="hud-cell-icon" data-hud-icon="${iconName}"></span><div><strong>${label}</strong><small id="qol-${id}-micro">SYS_ON</small></div></header><div class="qol-value" id="qol-${id}-value">LINKING</div><div class="qol-detail" id="qol-${id}-detail">CHANNEL SYNC</div>${meter || ""}</article>`;
  }

  function installTelemetryPanel() {
    const panel = document.getElementById("janeTelemetryPanel");
    if (!panel || panel.dataset.qolHud === VERSION) return Boolean(panel);
    panel.dataset.qolHud = VERSION;
    panel.style.clipPath = CUT;
    panel.innerHTML = `${bracketLayer()}<div class="telemetry-head qol-telemetry-head"><div><strong>CORE TELEMETRY</strong><small>SYS_ON // LIVE HARDWARE FEED</small></div><span id="qolTelemetryState">SYNCING</span></div><div id="qolTelemetryGrid" class="qol-telemetry-grid">` +
      cell("core", "INTEGRITY CORES", "zap", segmentStrip(14, "power-grid")) +
      cell("density", "QUANTUM CAPACITANCE", "database", '<div class="hud-radial" id="qol-density-radial"><span></span></div>') +
      cell("headroom", "NEURAL SYNC SYNERGY", "activity", segmentStrip(12, "reserve-grid")) +
      cell("load", "NEURAL OSCILLATION", "activity", '<canvas id="qolPulseCanvas" aria-label="Live neural oscillation trace"></canvas>') +
      cell("thermal", "THERMAL MATRIX", "thermometer", segmentStrip(10, "thermal-grid")) +
      cell("signal", "UPLINK LATTICE", "radio", segmentStrip(8, "signal-grid")) +
      cell("flux", "DATA FLUX", "activity", '<div class="hud-flux-track"><i id="qolFluxFill"></i></div>') +
      cell("continuity", "CONTINUITY CLOCK", "clock", '<div class="hud-ticks" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div>') +
      '</div>';
    panel.querySelectorAll("[data-hud-icon]").forEach(slot => slot.appendChild(icon(slot.dataset.hudIcon)));
    return true;
  }

  function replaceInterfaceIcons() {
    Object.entries(iconMap).forEach(([id, iconName]) => {
      const button = document.getElementById(id);
      if (!button) return;
      const podSlot = button.querySelector(".deck-pod-icon");
      if (podSlot) {
        if (!podSlot.querySelector(".lucide-hud")) podSlot.replaceChildren(icon(iconName));
      } else {
        if (button.querySelector(":scope > .lucide-hud")) return;
        button.querySelectorAll(":scope > svg").forEach(node => node.remove());
        const copy = Array.from(button.childNodes).filter(n => n.nodeType === Node.TEXT_NODE && n.textContent.trim());
        if (!copy.length || ["uploadBtn", "micBtn", "imageBtn", "privateBtn", "sendBtn"].includes(id)) {
          button.replaceChildren(icon(iconName));
        }
      }
    });

    const labButtons = document.querySelectorAll(".lab-console");
    if (labButtons[0] && !labButtons[0].querySelector(":scope > .lucide-hud")) {
      const firstSvg = labButtons[0].querySelector(":scope > svg");
      if (firstSvg) firstSvg.replaceWith(icon("audioLines"));
      else labButtons[0].prepend(icon("audioLines"));
    }
    if (labButtons[1] && !labButtons[1].querySelector(":scope > .lucide-hud")) {
      const firstSvg = labButtons[1].querySelector(":scope > svg");
      if (firstSvg) firstSvg.replaceWith(icon("hexagon"));
      else labButtons[1].prepend(icon("hexagon"));
    }

    document.querySelectorAll(".jane-back-button").forEach(button => {
      if (button.querySelector(".lucide-hud")) return;
      button.prepend(icon("arrowLeft"));
    });

    const greeting = document.getElementById("janeGreetingSignal");
    if (greeting && !greeting.querySelector(".lucide-hud")) {
      const label = greeting.querySelector("span")?.textContent || "GREETING SIGNAL";
      greeting.replaceChildren(icon("radio"), Object.assign(document.createElement("span"), { textContent: label }));
    }
  }

  function rebrandPrivateTelemetry() {
    const section = document.getElementById("janeTelemetryPermission");
    if (section && section.dataset.qolHud !== VERSION) {
      section.dataset.qolHud = VERSION;
      section.innerHTML = `${bracketLayer()}<div class="permission-copy"><strong>CHRONICLE LINK</strong><span>Optional day-cycle chronology. Core telemetry remains sealed locally and requires no authorization. <span id="qolChronicleState">LINK STATE UNKNOWN</span></span></div><button id="qolChronicleButton" type="button">AUTHORIZE CHRONICLE</button>`;
      section.querySelector("#qolChronicleButton")?.addEventListener("click", () => {
        try { window.AndroidJane?.openUsageAccessSettings?.(); } catch (error) {}
      });
      try {
        const granted = Boolean(window.AndroidJane?.hasUsageAccess?.());
        updateChronicleState(granted);
      } catch (error) {
        updateChronicleState(false, true);
      }
    }

    document.querySelectorAll("#janeSettingsNexus strong, #janeSettingsNexus small").forEach(node => {
      const text = node.textContent.trim();
      if (text === "MEMORY VAULT") node.textContent = "MNEMONIC VAULT";
      if (text === "NATIVE STORAGE") node.textContent = "SEALED ARCHIVE";
    });
  }

  function updateChronicleState(granted, unavailable) {
    const label = document.getElementById("qolChronicleState");
    const button = document.getElementById("qolChronicleButton");
    if (label) label.textContent = unavailable ? "CHRONICLE BRIDGE DARK" : granted ? "CHRONICLE LINKED" : "CHRONICLE SEALED";
    if (button) {
      button.textContent = granted ? "CHRONICLE LINKED" : "AUTHORIZE CHRONICLE";
      button.disabled = Boolean(granted);
    }
  }

  function shortGrid(bytes) {
    const value = Number(bytes || 0);
    if (value >= 1073741824) return `${(value / 1073741824).toFixed(value >= 10737418240 ? 1 : 2)}G`;
    if (value >= 1048576) return `${(value / 1048576).toFixed(0)}M`;
    return `${Math.max(0, Math.round(value / 1024))}K`;
  }

  function duration(ms) {
    const totalMinutes = Math.max(0, Math.floor(Number(ms || 0) / 60000));
    const days = Math.floor(totalMinutes / 1440);
    const hours = Math.floor((totalMinutes % 1440) / 60);
    const minutes = totalMinutes % 60;
    if (days) return `${days}D ${hours}H`;
    if (hours) return `${hours}H ${minutes}M`;
    return `${minutes}M`;
  }

  function relayName(kind) {
    return ({ wave: "WAVE RELAY", wide: "WIDE RELAY", hardline: "HARDLINE", veil: "VEIL TUNNEL", auxiliary: "AUX RELAY", dark: "DARK" })[String(kind)] || "AUX RELAY";
  }

  function thermalName(code) {
    return ({ equilibrium: "EQUILIBRIUM", rising: "RISING", "high-flux": "HIGH FLUX", guarded: "GUARDED", critical: "CRITICAL", emergency: "EMERGENCY", cutoff: "CUTOFF", unverified: "UNVERIFIED" })[String(code)] || "UNVERIFIED";
  }

  function coreHealthName(code) {
    return ({ nominal: "CORE NOMINAL", "heat-watch": "HEAT WATCH", critical: "CORE CRITICAL", "surge-watch": "SURGE WATCH", service: "SERVICE FLAG", "cold-lock": "COLD LOCK", unverified: "CORE UNVERIFIED" })[String(code)] || "CORE UNVERIFIED";
  }

  function coreFeedName(code) {
    return ({ "feed-saturated": "FEED SATURATED", "external-feed": "EXTERNAL FEED", saturated: "SATURATED", "free-cycle": "FREE CYCLE", holding: "HOLDING", unverified: "FEED UNVERIFIED" })[String(code)] || "FEED UNVERIFIED";
  }

  function toneFor(value, direction) {
    const v = Number(value);
    if (direction === "highBad") return v >= 88 ? "alert" : v >= 72 ? "amber" : "ice";
    return v <= 20 ? "alert" : v <= 40 ? "amber" : "mint";
  }

  function setTone(id, tone) {
    const el = document.getElementById(`qol-${id}`);
    if (el) el.dataset.tone = tone;
  }

  function setText(id, value, detail, micro) {
    const valueNode = document.getElementById(`qol-${id}-value`);
    const detailNode = document.getElementById(`qol-${id}-detail`);
    const microNode = document.getElementById(`qol-${id}-micro`);
    if (valueNode) valueNode.textContent = value;
    if (detailNode) detailNode.textContent = detail;
    if (microNode) microNode.textContent = micro;
  }

  function setSegments(id, percent) {
    const strip = document.querySelector(`#qol-${id} .hud-segments`);
    if (!strip) return;
    const items = Array.from(strip.children);
    const active = Math.round(clamp(Number(percent || 0), 0, 100) / 100 * items.length);
    items.forEach((item, index) => item.classList.toggle("active", index < active));
  }

  function updateStaticReadouts(data) {
    const core = Number(data.corePercent);
    setText("core", core >= 0 ? `${Math.round(telemetry.shown.core)}%` : "UNVERIFIED", `${coreHealthName(data.coreHealth)} // ${coreFeedName(data.coreFeed)}`, `CORE_GRID // ${data.coreFeed === "external-feed" ? "FEED_IN" : "SYS_ON"}`);
    setTone("core", core >= 0 ? toneFor(core, "lowBad") : "amber");

    const density = Number(data.gridDensityPercent || 0);
    setText("density", `${telemetry.shown.density.toFixed(1)}%`, `${shortGrid(data.gridUsedBytes)} / ${shortGrid(data.gridTotalBytes)} GRID`, `CELL_DENSITY // ${density > 88 ? "SAT_ALERT" : "NOMINAL"}`);
    setTone("density", toneFor(density, "highBad"));
    const radial = document.getElementById("qol-density-radial");
    if (radial) radial.style.setProperty("--radial", `${clamp(telemetry.shown.density, 0, 100) * 3.6}deg`);

    const headroom = Number(data.reserveHeadroomPercent || 0);
    setText("headroom", `${Math.round(telemetry.shown.headroom)}%`, `${shortGrid(data.reserveFreeBytes)} RESERVE POOL`, `SYNC_RESERVE // ${data.reservePressure ? "PRESSURE" : "NOMINAL"}`);
    setTone("headroom", data.reservePressure ? "alert" : toneFor(headroom, "lowBad"));

    const load = Number(data.arrayLoadPercent);
    setText("load", load >= 0 ? `${telemetry.shown.load.toFixed(1)}%` : "SAMPLING", `${Number(data.arrayCores || 1)} NODE ARRAY`, `OSC_SCOPE // ${data.arrayLoadScope === "array-total" ? "ARRAY_TOTAL" : "JANE_LOCAL"}`);
    setTone("load", load >= 85 ? "alert" : load >= 65 ? "amber" : "ice");

    const thermal = Number(data.thermalIndex);
    setText("thermal", thermalName(data.thermalCode), thermal >= 0 ? `FLUX INDEX ${Math.round(telemetry.shown.thermal)}` : "MATRIX UNVERIFIED", `HEAT_VECTOR // ${data.reserveProtocol ? "RESERVE_PROTOCOL" : "OPEN_CYCLE"}`);
    setTone("thermal", thermal >= 84 ? "alert" : thermal >= 44 ? "amber" : "mint");

    const signal = Number(data.relaySignalPercent);
    const relay = relayName(data.relayKind);
    setText("signal", data.relayLinked ? (signal >= 0 ? `${Math.round(telemetry.shown.signal)}%` : "LOCKED") : "DARK", data.relayLinked ? `${relay} // ${data.relayValidated ? "VERIFIED" : "OPEN"}` : "LOCAL CORE ONLY", `UPLINK_LOCK // ${data.relayLinked ? "ACTIVE" : "DARK"}`);
    setTone("signal", !data.relayLinked ? "amber" : signal >= 0 && signal < 25 ? "alert" : "mint");

    const traffic = data.traffic || {};
    const flux = Math.max(0, Number(traffic.rxKbps || 0) + Number(traffic.txKbps || 0));
    const advertised = Math.max(1, Number(data.relayDownKbps || 0) + Number(data.relayUpKbps || 0));
    const fluxPct = clamp((flux / advertised) * 100, 0, 100);
    telemetry.target.flux = fluxPct;
    setText("flux", flux >= 1000 ? `${(flux / 1000).toFixed(1)}M` : `${flux.toFixed(0)}K`, `IN ${Number(traffic.rxKbps || 0).toFixed(0)}K // OUT ${Number(traffic.txKbps || 0).toFixed(0)}K`, `DATA_SYNC // ${data.relayLinked ? "FLOW" : "SEALED"}`);
    setTone("flux", data.relayLinked ? "ice" : "amber");

    setText("continuity", duration(data.uptimeMs), "UNBROKEN ACTIVE SPAN", `CONTINUITY // ${VERSION}`);
    setTone("continuity", "ice");

    const state = document.getElementById("qolTelemetryState");
    if (state) state.textContent = data.hudError ? "DEGRADED" : "LIVE";
  }

  function pushSample(data) {
    const load = Math.max(0, Number(data.arrayLoadPercent || 0));
    const reserveUsed = Math.max(0, Number(data.reserveUsedPercent || 0));
    telemetry.samples.push({ t: performance.now(), load, reserveUsed });
    if (telemetry.samples.length > 72) telemetry.samples.shift();
  }

  function sampleTelemetry() {
    if (!window.AndroidHud || typeof window.AndroidHud.getTelemetry !== "function") {
      const state = document.getElementById("qolTelemetryState");
      if (state) state.textContent = "BRIDGE DARK";
      return;
    }
    try {
      const data = JSON.parse(window.AndroidHud.getTelemetry() || "{}");
      telemetry.raw = data;
      telemetry.target.core = Math.max(0, Number(data.corePercent || 0));
      telemetry.target.density = Math.max(0, Number(data.gridDensityPercent || 0));
      telemetry.target.headroom = Math.max(0, Number(data.reserveHeadroomPercent || 0));
      telemetry.target.reserveUsed = Math.max(0, Number(data.reserveUsedPercent || 0));
      telemetry.target.load = Math.max(0, Number(data.arrayLoadPercent || 0));
      telemetry.target.thermal = Math.max(0, Number(data.thermalIndex || 0));
      telemetry.target.signal = Math.max(0, Number(data.relaySignalPercent || 0));
      pushSample(data);
      try { updateChronicleState(Boolean(window.AndroidJane?.hasUsageAccess?.())); } catch (error) {}
    } catch (error) {
      const state = document.getElementById("qolTelemetryState");
      if (state) state.textContent = "DEGRADED";
    }
  }

  function animateMeters() {
    Object.keys(telemetry.shown).forEach(key => {
      telemetry.shown[key] = lerp(telemetry.shown[key], telemetry.target[key], 0.085);
    });
    setSegments("core", telemetry.shown.core);
    setSegments("headroom", telemetry.shown.headroom);
    setSegments("thermal", telemetry.shown.thermal);
    if (telemetry.raw?.relaySignalPercent >= 0) setSegments("signal", telemetry.shown.signal);
    else setSegments("signal", telemetry.raw?.relayLinked ? 100 : 0);
    const fill = document.getElementById("qolFluxFill");
    if (fill) fill.style.width = `${clamp(telemetry.shown.flux, 0, 100)}%`;
    if (telemetry.raw) updateStaticReadouts(telemetry.raw);
  }

  function drawPulse(now) {
    const canvas = document.getElementById("qolPulseCanvas");
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    if (rect.width < 4 || rect.height < 4) return;
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const width = Math.round(rect.width * dpr);
    const height = Math.round(rect.height * dpr);
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    const ctx = canvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const w = rect.width;
    const h = rect.height;
    ctx.clearRect(0, 0, w, h);

    ctx.lineWidth = 0.5;
    ctx.strokeStyle = "rgba(93, 225, 255, .11)";
    for (let x = 0; x <= w; x += Math.max(12, w / 12)) {
      ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke();
    }
    for (let y = 0; y <= h; y += Math.max(10, h / 5)) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke();
    }

    const load = clamp(telemetry.shown.load, 0, 100);
    const reserve = clamp(telemetry.shown.reserveUsed, 0, 100);
    const amplitude = h * (0.06 + load * 0.0030 + reserve * 0.0007);
    const cycles = 1.5 + load * 0.035;
    const phase = now * (0.0022 + load * 0.000012);
    ctx.beginPath();
    for (let x = 0; x <= w; x += 2) {
      const p = x / Math.max(1, w);
      const carrier = Math.sin(p * Math.PI * 2 * cycles + phase);
      const harmonic = Math.sin(p * Math.PI * 2 * (cycles * 2.1) - phase * 0.55) * (reserve / 100) * 0.22;
      const y = h / 2 + (carrier + harmonic) * amplitude;
      if (x === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
    }
    ctx.lineWidth = 1.25;
    ctx.strokeStyle = load >= 85 ? "rgba(255, 73, 92, .95)" : load >= 65 ? "rgba(255, 181, 57, .95)" : "rgba(72, 230, 255, .95)";
    ctx.shadowBlur = 7;
    ctx.shadowColor = ctx.strokeStyle;
    ctx.stroke();
    ctx.shadowBlur = 0;

    if (telemetry.samples.length > 1) {
      ctx.beginPath();
      telemetry.samples.forEach((sample, index) => {
        const x = (index / Math.max(1, telemetry.samples.length - 1)) * w;
        const y = h - (sample.load / 100) * h;
        if (index === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      });
      ctx.lineWidth = 0.7;
      ctx.strokeStyle = "rgba(188, 251, 255, .55)";
      ctx.stroke();
    }
  }

  function frame(now) {
    animateMeters();
    drawPulse(now);
    telemetry.frame = requestAnimationFrame(frame);
  }

  function mechanicalize() {
    const selectors = [
      ".deck-panel", ".deck-nav-pod", ".jane-scene-frame", ".jane-chat-console",
      ".jane-portrait-bay", ".settings-card", ".settings-section", ".lab-console",
      ".v79-archive-card", ".v61Card", ".v65Card", ".prompt-card"
    ];
    document.querySelectorAll(selectors.join(",")).forEach(node => {
      node.classList.add("qol-machined");
      node.style.borderRadius = "0";
      node.style.clipPath = CUT;
    });
  }

  function refresh() {
    document.body.classList.add("jane-qol-hud");
    const ready = installTelemetryPanel();
    replaceInterfaceIcons();
    rebrandPrivateTelemetry();
    mechanicalize();
    if (!ready) return false;
    if (!telemetry.installed) {
      telemetry.installed = true;
      sampleTelemetry();
      telemetry.poll = window.setInterval(sampleTelemetry, 1000);
      telemetry.frame = requestAnimationFrame(frame);
    }
    return true;
  }

  let attempts = 0;
  const boot = window.setInterval(() => {
    attempts += 1;
    if (refresh() || attempts > 40) window.clearInterval(boot);
  }, 150);

  window.setInterval(() => {
    if (!telemetry.installed || !document.getElementById("qolTelemetryGrid")) refresh();
    else { replaceInterfaceIcons(); rebrandPrivateTelemetry(); mechanicalize(); }
  }, 2500);

  window.JaneQolHud = { version: VERSION, refresh, sample: sampleTelemetry };
  refresh();
})();
