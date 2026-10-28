(function () {
  "use strict";

  if (window.__MONOLITH_FUNCTIONALITY_RUNTIME__) return;
  window.__MONOLITH_FUNCTIONALITY_RUNTIME__ = true;

  const $ = (selector, root = document) => root.querySelector(selector);
  const pending = new Map();
  let requestCounter = 0;
  let previousAnswerHandler = null;

  function decodePayload(encoded) {
    try {
      const binary = atob(String(encoded || ""));
      const bytes = Uint8Array.from(binary, c => c.charCodeAt(0));
      return JSON.parse(new TextDecoder("utf-8").decode(bytes));
    } catch (_) {
      return { error: "Jane returned a response that could not be decoded." };
    }
  }

  function transcript() {
    const consolePanel = $(".jane-chat-console") || $("#vn .dialog")?.parentElement;
    if (!consolePanel) return null;
    let node = $("#monolithChatTranscript", consolePanel);
    if (!node) {
      node = document.createElement("section");
      node.id = "monolithChatTranscript";
      node.setAttribute("aria-live", "polite");
      node.setAttribute("aria-label", "Conversation transcript");
      node.style.cssText = "display:flex;flex-direction:column;gap:8px;overflow:auto;min-height:0;max-height:42%;padding:8px 5px;margin:4px 0;border-bottom:1px solid rgba(0,229,255,.16);font:500 12px/1.45 system-ui,sans-serif;color:#d5e7ed;scrollbar-width:thin;";
      const composer = $("#userInput", consolePanel)?.closest("form") || $("#userInput", consolePanel)?.parentElement;
      if (composer && composer.parentElement === consolePanel) consolePanel.insertBefore(node, composer);
      else consolePanel.appendChild(node);
    }
    return node;
  }

  function addLine(kind, message) {
    const target = transcript();
    if (!target) return;
    const row = document.createElement("div");
    row.dataset.messageRole = kind;
    row.style.cssText = "max-width:94%;align-self:" + (kind === "user" ? "flex-end" : "flex-start") +
      ";white-space:pre-wrap;overflow-wrap:anywhere;padding:7px 10px;border-left:2px solid " +
      (kind === "user" ? "#ff963d" : "#28c8dc") +
      ";background:rgba(5,18,27,.84);box-shadow:inset 0 0 12px rgba(0,229,255,.04);";
    const label = document.createElement("strong");
    label.textContent = kind === "user" ? "YOU // " : "JANE // ";
    label.style.cssText = "display:block;font:700 9px ui-monospace,monospace;letter-spacing:.12em;color:" +
      (kind === "user" ? "#ffad60" : "#59dbe7") + ";margin-bottom:3px;";
    const body = document.createElement("span");
    body.textContent = message;
    row.append(label, body);
    target.appendChild(row);
    target.scrollTop = target.scrollHeight;
    return body;
  }

  function finishRequest(id, payload) {
    const entry = pending.get(String(id));
    if (!entry) return false;
    pending.delete(String(id));
    entry.body.textContent = payload && payload.reply
      ? payload.reply
      : "I couldn't complete that response. " + String(payload?.error || "The local AI runtime returned no answer.");
    entry.body.parentElement.dataset.responseState = payload && payload.reply ? "complete" : "error";
    const input = $("#userInput");
    const send = $("#sendBtn");
    if (input) input.disabled = false;
    if (send) send.disabled = false;
    return true;
  }

  function installNativeAnswerHook() {
    if (window.JaneNativeOfflineKnowledgeAnswerResult?.__monolithWrapped) return;
    previousAnswerHandler = window.JaneNativeOfflineKnowledgeAnswerResult;
    const handler = function (requestId, encoded) {
      const payload = decodePayload(encoded);
      if (!finishRequest(requestId, payload) && typeof previousAnswerHandler === "function") {
        try { previousAnswerHandler.apply(this, arguments); } catch (_) {}
      }
    };
    handler.__monolithWrapped = true;
    window.JaneNativeOfflineKnowledgeAnswerResult = handler;
  }

  function sendMessage() {
    const input = $("#userInput");
    if (!input) return false;
    const message = String(input.value || "").trim();
    if (!message) {
      input.focus();
      return true;
    }
    const bridge = window.AndroidJane;
    if (!bridge || typeof bridge.answerKnowledgeOffline !== "function") {
      addLine("assistant", "The native conversation bridge is unavailable. Reopen Monolith AI after the core finishes loading.");
      return true;
    }

    const id = "mono-chat-" + Date.now().toString(36) + "-" + (++requestCounter);
    addLine("user", message);
    const replyBody = addLine("assistant", "Connecting to Jane's on-device language model…");
    if (!replyBody) return false;
    pending.set(id, { body: replyBody });
    input.value = "";
    input.disabled = true;
    const send = $("#sendBtn");
    if (send) send.disabled = true;

    try {
      installNativeAnswerHook();
      const ownerVerified = Boolean(window.__MONOLITH_OWNER_VERIFIED || window.JaneOwnerVerified);
      bridge.answerKnowledgeOffline(id, message, ownerVerified);
      window.setTimeout(() => {
        if (pending.has(id)) finishRequest(id, { error: "The local AI request timed out. Check that the on-device model is installed and available." });
      }, 120000);
    } catch (error) {
      finishRequest(id, { error: error?.message || "The native chat request could not start." });
    }
    return true;
  }

  function bindNavigationFallback() {
    if (document.documentElement.dataset.monolithNavigationFallbackBound === "true") return;
    document.documentElement.dataset.monolithNavigationFallbackBound = "true";
    const routes = {
      janeNavChat: "chat",
      janeNavArchives: "archives",
      janeNavStudio: "studio",
      janeNavSettings: "settings",
      monolithNavModel: "model",
      monolithNavVoice: "voice",
      monolithNavRpg: "rpg"
    };
    document.addEventListener("click", event => {
      const button = event.target && event.target.closest
        ? event.target.closest("#janeNavChat,#janeNavArchives,#janeNavStudio,#janeNavSettings,#monolithNavModel,#monolithNavVoice,#monolithNavRpg,.deck-nav-pod")
        : null;
      if (!button || button.disabled) return;
      const route = routes[button.id] || button.dataset.monolithRoute;
      if (!route) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      const router = window.JaneSceneRouter;
      if (router && typeof router.open === "function") {
        router.open(route, route === "chat" ? { initialPortrait: true } : undefined);
      } else if (window.MonolithCore && typeof window.MonolithCore.open === "function") {
        window.MonolithCore.open(route);
      } else {
        document.documentElement.dataset.monolithNavigationError = "router-unavailable";
      }
    }, true);
  }

  function bindChat() {
    if (document.documentElement.dataset.monolithChatEventsBound === "true") return;
    document.documentElement.dataset.monolithChatEventsBound = "true";
    document.addEventListener("click", event => {
      const button = event.target && event.target.closest ? event.target.closest("#sendBtn") : null;
      if (!button) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      sendMessage();
    }, true);
    document.addEventListener("keydown", event => {
      if (!event.target || !event.target.matches || !event.target.matches("#userInput")) return;
      if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        event.stopImmediatePropagation();
        sendMessage();
      }
    }, true);
  }

  function ensureModelViewer() {
    const stage = $("#home .home-stage");
    if (!stage) return;
    const sourceNode = $("#homeJaneModel");
    const source = sourceNode
      ? (sourceNode.getAttribute("src") || sourceNode.getAttribute("data-src") || "")
      : "";
    if (!source.trim()) {
      stage.dataset.monolithGlbState = "asset-missing";
      const status = $(".monolith-glb-status", stage) || document.createElement("div");
      status.className = "monolith-glb-status";
      if (status.textContent !== "JANE GLB ASSET NOT BUNDLED") status.textContent = "JANE GLB ASSET NOT BUNDLED";
      status.style.cssText = "position:absolute;z-index:9;left:50%;top:68%;transform:translateX(-50%);font:700 9px ui-monospace,monospace;letter-spacing:.12em;color:#e6a15e;text-align:center;pointer-events:none;";
      if (!status.parentNode) stage.appendChild(status);
      return;
    }

    if (!document.getElementById("monolith-local-model-viewer")) {
      const script = document.createElement("script");
      script.id = "monolith-local-model-viewer";
      script.src = "file:///android_asset/model-viewer-umd.min.js";
      script.addEventListener("error", () => { stage.dataset.monolithGlbState = "viewer-load-error"; }, { once: true });
      script.addEventListener("load", mount, { once: true });
      document.head.appendChild(script);
    } else if (window.customElements?.get("model-viewer")) {
      mount();
    } else {
      document.getElementById("monolith-local-model-viewer").addEventListener("load", mount, { once: true });
    }

    function mount() {
      if (!window.customElements?.get("model-viewer")) {
        stage.dataset.monolithGlbState = "viewer-not-defined";
        return;
      }
      let viewer = $("#monolithHologramViewer", stage);
      if (!viewer) {
        viewer = document.createElement("model-viewer");
        viewer.id = "monolithHologramViewer";
        viewer.setAttribute("camera-controls", "");
        viewer.setAttribute("auto-rotate", "");
        viewer.setAttribute("interaction-prompt", "none");
        viewer.setAttribute("shadow-intensity", "1");
        viewer.setAttribute("exposure", "1.1");
        viewer.setAttribute("camera-orbit", "0deg 75deg 115%");
        viewer.style.cssText = "position:absolute;inset:4% 8% 8%;width:84%;height:88%;z-index:5;background:transparent;--poster-color:transparent;";
        stage.appendChild(viewer);
      }
      if (viewer.dataset.monolithModelBound === "true") return;
      viewer.dataset.monolithModelBound = "true";
      viewer.setAttribute("src", source);
      viewer.addEventListener("load", () => {
        stage.dataset.monolithGlbState = "loaded";
        $(".monolith-glb-status", stage)?.remove();
      }, { once: true });
      viewer.addEventListener("error", () => {
        stage.dataset.monolithGlbState = "model-load-error";
        const status = $(".monolith-glb-status", stage) || document.createElement("div");
        status.className = "monolith-glb-status";
        status.textContent = "GLB LOAD FAILED // CHECK LOCAL ASSET PATH";
        status.style.cssText = "position:absolute;z-index:9;left:50%;top:68%;transform:translateX(-50%);font:700 9px ui-monospace,monospace;letter-spacing:.12em;color:#e6a15e;text-align:center;pointer-events:none;";
        if (!status.parentNode) stage.appendChild(status);
      }, { once: true });
    }
  }

  function refresh() {
    bindNavigationFallback();
    bindChat();
    ensureModelViewer();
  }

  let refreshQueued = false;
  function scheduleRefresh() {
    if (refreshQueued) return;
    refreshQueued = true;
    window.requestAnimationFrame(() => {
      refreshQueued = false;
      refresh();
    });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", refresh, { once: true });
  else refresh();
  // Avoid a document-wide observer here. Scene initialization has its own router lifecycle;
  // repeated DOM scans during boot can stall WebView input and the live HUD animation loop.
  window.addEventListener("jane-scene-change", scheduleRefresh);
  window.addEventListener("resize", scheduleRefresh, { passive: true });
  window.MonolithFunctionalityRuntime = { refresh, sendMessage, scheduleRefresh };
})();