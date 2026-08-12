(function () {
  "use strict";

  const VERSION = "MONOLITH-FINAL-UI-4";
  const HARDWARE_CSS_ID = "monolith-hardware-gen3-css";
  const HARDWARE_CSS_PATH = "file:///android_asset/monolith_hardware_gen3.css";

  if (window.MonolithFinalUi && window.MonolithFinalUi.version === VERSION) {
    window.MonolithFinalUi.refresh();
    return;
  }

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

  function setText(node, value) {
    if (node && node.textContent !== value) node.textContent = value;
  }

  function ensureHardwareCss() {
    let link = document.getElementById(HARDWARE_CSS_ID);
    if (link) return link;
    link = document.createElement("link");
    link.id = HARDWARE_CSS_ID;
    link.rel = "stylesheet";
    link.href = HARDWARE_CSS_PATH;
    link.dataset.monolithLoadState = "loading";
    link.addEventListener("load", () => {
      link.dataset.monolithLoadState = "loaded";
      document.documentElement.dataset.monolithHardwareUi = "ready";
    }, { once: true });
    link.addEventListener("error", () => {
      link.dataset.monolithLoadState = "error";
      document.documentElement.dataset.monolithHardwareUi = "error";
    }, { once: true });
    document.head.appendChild(link);
    return link;
  }

  function removeDeprecatedLayers() {
    document.getElementById("janeVitalsHUD")?.remove();
    document.getElementById("monolithModuleOverlay")?.remove();
    $$(".jane-vitals-column,.jane-vital-card,.vital-card").forEach(node => node.remove());
  }

  function normalizeCommandLabels() {
    setText($("#janeTelemetryPanel .telemetry-head strong"), "CORE TELEMETRY");
    setText($("#janeTelemetryPanel .telemetry-head span"), "LIVE // LOCAL");
    setText($("#janeMenuPanel .menu-head strong"), "MODULE LINKS");
    setText($("#janeMenuPanel .menu-head span"), "SELECT SCENE");
    setText($(".deck-core-title"), "COMMAND CHAMBER");

    const replacements = {
      "telemetry-core": "INTEGRITY CORE",
      "telemetry-vault": "MEMORY VAULT",
      "telemetry-reserve": "COGNITIVE RESERVE",
      "telemetry-cycle": "ACTIVE CYCLE",
      "telemetry-thermal": "THERMAL MATRIX",
      "telemetry-uplink": "UPLINK LATTICE",
      "telemetry-continuity": "SYSTEM CONTINUITY"
    };
    Object.entries(replacements).forEach(([id, label]) => setText($(`#${id} .telemetry-label`), label));
  }

  function installStageHardware() {
    const stage = $("#home .home-stage");
    if (!stage) return;
    if (!$(".mono-stage-tag.top", stage)) {
      const top = document.createElement("div");
      top.className = "mono-stage-tag top";
      top.innerHTML = "<span>HOLO BODY // LOCAL GLB</span><i></i><i></i><i></i>";
      stage.appendChild(top);
    }
    if (!$(".mono-stage-tag.bottom", stage)) {
      const bottom = document.createElement("div");
      bottom.className = "mono-stage-tag bottom";
      bottom.innerHTML = "<b>PROJECTOR FIELD</b><span>RENDER CHANNEL // STABLE</span>";
      stage.appendChild(bottom);
    }
    if (!$(".mono-stage-bracket.left", stage)) {
      ["left", "right"].forEach(side => {
        const bracket = document.createElement("div");
        bracket.className = `mono-stage-bracket ${side}`;
        bracket.setAttribute("aria-hidden", "true");
        stage.appendChild(bracket);
      });
    }
  }

  function installPanelGreebles() {
    $$("#janeTelemetryPanel,#janeMenuPanel,.hardware-panel,.jane-chat-console,.jane-portrait-bay").forEach(panel => {
      if ($(".mono-greeble-rail", panel)) return;
      const rail = document.createElement("div");
      rail.className = "mono-greeble-rail";
      rail.setAttribute("aria-hidden", "true");
      rail.innerHTML = "<i></i><i></i><i></i><i></i><i></i>";
      panel.appendChild(rail);
    });
  }

  function normalizeChat() {
    const input = $("#userInput");
    if (input && input.value.trim() === "Tell me a riddle.") input.value = "";
    if (input && input.placeholder !== "Enter command, question, or archive request...") {
      input.placeholder = "Enter command, question, or archive request...";
    }
    const consolePanel = $(".jane-chat-console");
    if (consolePanel && !$(".mono-console-label", consolePanel)) {
      const label = document.createElement("div");
      label.className = "mono-console-label";
      label.innerHTML = "<span>CONVERSATION BUS</span><b>LOCAL AI CHANNEL</b>";
      consolePanel.prepend(label);
    }
  }

  function auditExclusiveScene() {
    const host = document.getElementById("janeSceneHost");
    if (!host) return;
    const active = $$(":scope > [data-jane-scene][data-jane-active='true']", host);
    if (active.length <= 1) return;
    const preferredName = document.body.dataset.janeScene;
    const preferred = active.find(scene => scene.dataset.janeScene === preferredName) || active[active.length - 1];
    active.forEach(scene => {
      const keep = scene === preferred;
      scene.dataset.janeActive = keep ? "true" : "false";
      scene.setAttribute("aria-hidden", keep ? "false" : "true");
    });
  }

  function forceCommandScene(reason) {
    const host = document.getElementById("janeSceneHost");
    const command = host && host.querySelector(':scope > [data-jane-scene="command"]');
    if (!host || !command) return false;

    document.body.classList.add("jane-deck-launched", "monolith-owner-authorized");
    host.setAttribute("aria-hidden", "false");

    host.querySelectorAll(":scope > [data-jane-scene]").forEach(scene => {
      const active = scene === command;
      scene.dataset.janeActive = active ? "true" : "false";
      scene.setAttribute("aria-hidden", active ? "false" : "true");
    });

    command.classList.remove("hidden");
    document.getElementById("vn")?.classList.remove("active");
    document.getElementById("janeKnowledgeBase")?.classList.remove("open");
    document.getElementById("ownerModal")?.classList.remove("active");
    document.getElementById("janeMeshyStudio")?.classList.remove("open");
    document.getElementById("janeMusicStudio")?.classList.remove("open");
    document.body.classList.remove("v79-knowledge-open", "jane-overlay-open", "jane-studio-open", "jane-settings-open");

    document.body.dataset.janeScene = "command";
    document.documentElement.dataset.monolithForcedCommand = reason || "direct";
    document.documentElement.dataset.monolithSceneMounted = "command";

    try {
      history.replaceState({ janeScene: "command", monolithDirectHandoff: true }, "", "#command");
    } catch (_) {}

    try {
      window.dispatchEvent(new CustomEvent("jane-scene-change", { detail: { scene: "command", direct: true } }));
    } catch (_) {}

    const cs = getComputedStyle(command);
    const rect = command.getBoundingClientRect();
    const visible = (
      command.dataset.janeActive === "true" &&
      cs.display !== "none" &&
      cs.visibility !== "hidden" &&
      Number.parseFloat(cs.opacity || "1") > 0.02 &&
      rect.width > 100 &&
      rect.height > 100
    );

    document.documentElement.dataset.monolithDirectHandoff = visible ? "visible" : "state-set";
    return true;
  }

  function addRuntimeCss() {
    if (document.getElementById("monolith-final-ui-runtime-css")) return;
    const style = document.createElement("style");
    style.id = "monolith-final-ui-runtime-css";
    style.textContent = `
      .mono-stage-tag{position:absolute;z-index:8;pointer-events:none;font:700 6px ui-monospace,monospace;letter-spacing:.12em;color:#6f99a7;text-transform:uppercase}
      .mono-stage-tag.top{top:10px;left:13px;display:flex;align-items:center;gap:5px}.mono-stage-tag.top i{display:block;width:8px;height:2px;background:#00e5ff;opacity:.36}.mono-stage-tag.top i:last-child{background:#ff6d00;opacity:.8}
      .mono-stage-tag.bottom{bottom:10px;right:13px;display:grid;text-align:right;gap:2px}.mono-stage-tag.bottom b{color:#ff9b3d;font-size:7px}.mono-stage-tag.bottom span{color:#567b88}
      .mono-stage-bracket{position:absolute;z-index:7;top:20%;bottom:20%;width:14px;pointer-events:none;border-top:1px solid rgba(0,229,255,.24);border-bottom:1px solid rgba(0,229,255,.24)}.mono-stage-bracket.left{left:9px;border-left:2px solid rgba(0,229,255,.32)}.mono-stage-bracket.right{right:9px;border-right:2px solid rgba(255,109,0,.32)}
      .mono-greeble-rail{position:absolute;z-index:8;right:10px;bottom:9px;display:flex;gap:3px;pointer-events:none}.mono-greeble-rail i{width:5px;height:2px;background:rgba(0,229,255,.34)}.mono-greeble-rail i:nth-child(4),.mono-greeble-rail i:nth-child(5){background:#ff6d00;opacity:.68}
      .mono-console-label{position:relative;z-index:4;height:22px;display:flex;align-items:center;justify-content:space-between;padding:0 6px 5px;margin-bottom:2px;border-bottom:1px solid rgba(0,229,255,.09);color:#648b98;font:700 6px ui-monospace,monospace;letter-spacing:.1em}.mono-console-label b{color:#00e5ff;font-weight:800}
    `;
    document.head.appendChild(style);
  }

  function refresh() {
    document.body.classList.add("monolith-final-ui");
    ensureHardwareCss();
    removeDeprecatedLayers();
    addRuntimeCss();
    normalizeCommandLabels();
    installStageHardware();
    normalizeChat();
    installPanelGreebles();
    auditExclusiveScene();
    return true;
  }

  window.addEventListener("jane-scene-change", () => requestAnimationFrame(refresh));
  window.addEventListener("resize", () => requestAnimationFrame(refresh));

  const observer = new MutationObserver(() => requestAnimationFrame(refresh));
  observer.observe(document.documentElement, { childList: true, subtree: true });

  window.MonolithFinalUi = {
    version: VERSION,
    refresh,
    forceCommandScene
  };

  refresh();
})();
