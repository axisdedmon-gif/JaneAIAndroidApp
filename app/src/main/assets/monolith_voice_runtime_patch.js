(function () {
  "use strict";

  const VERSION = "MONOLITH-VOICE-PATCH-2";
  if (window.MonolithVoiceRuntimePatch && window.MonolithVoiceRuntimePatch.version === VERSION) {
    window.MonolithVoiceRuntimePatch.apply();
    return;
  }

  function ensureSceneRuntime() {
    if (window.MonolithSceneRuntime) {
      window.MonolithSceneRuntime.refresh?.();
      return;
    }
    if (document.getElementById("monolith-scene-runtime-js")) return;
    const script = document.createElement("script");
    script.id = "monolith-scene-runtime-js";
    script.src = "file:///android_asset/monolith_scene_runtime.js";
    document.head.appendChild(script);
  }

  function parseState() {
    try {
      return window.AndroidMonolith ? JSON.parse(AndroidMonolith.getVoiceWorkspace() || "{}") : {};
    } catch (_) {
      return {};
    }
  }

  function voiceRoot() {
    return window.MonolithSceneRuntime?.sceneFor?.("voice")
      || document.querySelector('[data-jane-scene="monolith-voice"]');
  }

  function apply() {
    ensureSceneRuntime();

    if (document.querySelector("#janeMenuPanel .deck-menu-list") && !document.getElementById("monolithNavModel")) {
      window.MonolithCore?.refresh?.();
    }

    const root = voiceRoot();
    if (!root) return;
    const voicePanel = root.querySelector(".monolith-voice-grid");
    if (!voicePanel) return;

    const state = parseState();
    const models = new Map((state.models || []).map(model => [String(model.id), model]));

    root.querySelectorAll("[data-voice-model]").forEach(button => {
      const row = models.get(String(button.dataset.voiceModel));
      if (!row) return;
      button.disabled = !row.runnable;
      if (row.active) button.textContent = "ACTIVE // LOCAL";
      else if (row.runnable) button.textContent = "ACTIVATE LOCAL";
      else button.textContent = "CONVERSION REQUIRED";
    });

    const datasets = state.datasets || [];
    const datasetList = voicePanel.querySelector(".voice-dataset .voice-list");
    if (datasetList) {
      Array.from(datasetList.querySelectorAll("article")).forEach(article => {
        const id = (article.querySelector("strong")?.textContent || "").trim();
        const data = datasets.find(dataset => String(dataset.id) === id);
        if (!data || article.querySelector("[data-export-dataset]")) return;

        const button = document.createElement("button");
        button.type = "button";
        button.dataset.exportDataset = id;
        button.textContent = data.exportable ? "EXPORT DATASET ZIP" : "DATASET INCOMPLETE";
        button.disabled = !data.exportable;
        button.addEventListener("click", () => {
          try {
            AndroidMonolith.exportVoiceDataset(id);
          } catch (_) {
            window.MonolithVoiceNotice?.("ERROR: Dataset export bridge unavailable.");
          }
        });
        article.appendChild(button);
      });
    }

    const reactor = voicePanel.querySelector(".voice-reactor em");
    if (reactor) {
      const active = state.activeModel ? `LOCAL MODEL // ${state.activeModel}` : "LOCAL MODEL SLOT // EMPTY";
      reactor.textContent = `${active} // ${String(state.runtimeState || "inactive").toUpperCase()}`;
    }

    const framework = voicePanel.querySelector(".voice-framework small");
    if (framework) {
      framework.textContent = "ON-DEVICE DATASET CAPTURE // PIPER OFFLINE TRAINING // SHERPA CONVERSION // LOCAL SYSTEMIC SPEECH";
    }
  }

  window.MonolithVoiceNotice = function (message) {
    const target = voiceRoot()?.querySelector("#voiceRecordState");
    if (target) target.textContent = String(message || "");
  };

  const observer = new MutationObserver(() => apply());
  observer.observe(document.documentElement, { childList: true, subtree: true });

  window.MonolithVoiceRuntimePatch = { version: VERSION, apply };
  setInterval(apply, 1200);
  ensureSceneRuntime();
  apply();
})();
