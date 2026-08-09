(function () {
  "use strict";

  const VERSION = "MONOLITH-SCENE-2";
  if (window.MonolithSceneRuntime && window.MonolithSceneRuntime.version === VERSION) {
    window.MonolithSceneRuntime.refresh();
    return;
  }

  const MODULE_ROUTES = Object.freeze({
    model: "monolith-model",
    voice: "monolith-voice",
    rpg: "monolith-rpg"
  });

  let baseRouter = null;
  let routedRouter = null;
  let activeExternal = "";
  let returnScene = "command";
  let finishing = false;
  const externalScenes = new Map();

  function loadStyle(id, href) {
    if (document.getElementById(id)) return;
    const link = document.createElement("link");
    link.id = id;
    link.rel = "stylesheet";
    link.href = href;
    document.head.appendChild(link);
  }

  function loadScript(id, src, onload) {
    const existing = document.getElementById(id);
    if (existing) {
      if (onload) setTimeout(onload, 0);
      return;
    }
    const script = document.createElement("script");
    script.id = id;
    script.src = src;
    if (onload) script.addEventListener("load", onload, { once: true });
    document.head.appendChild(script);
  }

  function commandDeckInitialized() {
    return Boolean(window.JaneSceneRouter || document.body.classList.contains("jane-deck-ready"));
  }

  function purgeLegacyLayers() {
    // #ownerGate is a hidden compatibility anchor required only while jane_command_deck.js
    // builds its initial scene graph. Delete it permanently after that graph exists.
    if (commandDeckInitialized()) document.getElementById("ownerGate")?.remove();
    document.getElementById("janeVitalsHUD")?.remove();
    document.querySelectorAll(".jane-vitals-column,.jane-vital-card,.vital-card").forEach(node => node.remove());
    document.getElementById("monolithModuleOverlay")?.remove();
  }

  function sceneHost() {
    return document.getElementById("janeSceneHost");
  }

  function setSceneVisible(scene, visible) {
    if (!scene) return;
    scene.dataset.janeActive = visible ? "true" : "false";
    scene.setAttribute("aria-hidden", visible ? "false" : "true");
  }

  function ensureExternalScene(route) {
    const host = sceneHost();
    if (!host) return null;

    let scene = host.querySelector(`:scope > [data-jane-scene="${route}"]`);
    if (!scene) {
      scene = document.createElement("section");
      scene.className = "monolith-scene-root";
      scene.dataset.janeScene = route;
      scene.dataset.janeActive = "false";
      scene.setAttribute("aria-hidden", "true");
      host.appendChild(scene);
    }
    externalScenes.set(route, scene);
    return scene;
  }

  function ensureExternalScenes() {
    if (!sceneHost()) return false;
    Object.values(MODULE_ROUTES).forEach(ensureExternalScene);
    return true;
  }

  function hideExternalScenes() {
    externalScenes.forEach(scene => setSceneVisible(scene, false));
  }

  function routeName(name) {
    const raw = String(name || "").trim();
    return MODULE_ROUTES[raw] || raw;
  }

  function showExternalScene(route, options) {
    const host = sceneHost();
    const target = externalScenes.get(route) || ensureExternalScene(route);
    if (!host || !target) return false;

    if (!activeExternal) {
      const prior = typeof baseRouter?.current === "function" ? baseRouter.current() : "command";
      if (prior && !externalScenes.has(prior)) returnScene = prior;
    }

    host.setAttribute("aria-hidden", "false");
    document.body.classList.add("jane-deck-launched");
    host.querySelectorAll(":scope > [data-jane-scene]").forEach(scene => {
      setSceneVisible(scene, scene === target);
    });

    activeExternal = route;
    document.body.dataset.janeScene = route;
    window.dispatchEvent(new CustomEvent("jane-scene-change", { detail: { scene: route } }));

    const opts = options || {};
    if (opts.push === true) {
      try {
        history.pushState({ janeScene: route, monolithExternal: true }, "", `#${route}`);
      } catch (_) {}
    }
    return true;
  }

  function installRouter() {
    if (routedRouter && window.JaneSceneRouter === routedRouter) return true;
    if (!window.JaneSceneRouter || typeof window.JaneSceneRouter.open !== "function") return false;

    if (window.JaneSceneRouter.__monolithExclusiveRouter) {
      routedRouter = window.JaneSceneRouter;
      return true;
    }

    baseRouter = window.JaneSceneRouter;
    if (!ensureExternalScenes()) return false;

    routedRouter = {
      __monolithExclusiveRouter: true,

      open(name, options) {
        const route = routeName(name);
        if (externalScenes.has(route) || Object.values(MODULE_ROUTES).includes(route)) {
          return showExternalScene(route, options);
        }

        activeExternal = "";
        hideExternalScenes();
        const result = baseRouter.open(route, options);
        document.body.dataset.janeScene = route;
        return result;
      },

      back() {
        if (activeExternal) {
          const target = returnScene || "command";
          activeExternal = "";
          hideExternalScenes();
          return baseRouter.open(target, { push: false, replace: true, cue: "back" });
        }
        if (typeof baseRouter.back === "function") return baseRouter.back();
        return baseRouter.open("command", { push: false, replace: true, cue: "back" });
      },

      current() {
        if (activeExternal) return activeExternal;
        return typeof baseRouter.current === "function" ? baseRouter.current() : "command";
      },

      register(name, element) {
        const route = routeName(name);
        const host = sceneHost();
        if (!host || !route || !element) return false;
        element.dataset.janeScene = route;
        element.dataset.janeActive = "false";
        element.setAttribute("aria-hidden", "true");
        if (element.parentNode !== host) host.appendChild(element);
        externalScenes.set(route, element);
        return true;
      }
    };

    window.JaneSceneRouter = routedRouter;
    purgeLegacyLayers();
    return true;
  }

  function finishDeck() {
    if (finishing) return;
    finishing = true;
    try {
      if (!window.JaneSceneRouter) {
        finishing = false;
        setTimeout(finishDeck, 40);
        return;
      }
      installRouter();
      ensureExternalScenes();
      purgeLegacyLayers();
      window.MonolithCore?.refresh?.();
      window.JaneQolHud?.refresh?.();
      window.MonolithFinalUi?.refresh?.();
    } finally {
      finishing = false;
    }
  }

  function ensureCommandDeck() {
    loadStyle("jane-command-deck-css", "file:///android_asset/jane_command_deck.css");
    loadStyle("monolith-final-ui-css", "file:///android_asset/monolith_final_ui.css");
    loadScript("monolith-final-ui-js", "file:///android_asset/monolith_final_ui.js");

    if (window.JaneSceneRouter) {
      finishDeck();
      return;
    }
    loadScript("jane-command-deck-js", "file:///android_asset/jane_command_deck.js", finishDeck);
  }

  const style = document.createElement("style");
  style.id = "monolith-scene-runtime-css";
  style.textContent = `
    #ownerGate,#janeVitalsHUD,.jane-vitals-column,.jane-vital-card,.vital-card{display:none!important}
    #janeSceneHost{isolation:isolate!important}
    #janeSceneHost>[data-jane-scene]{pointer-events:none!important;visibility:hidden!important;opacity:0!important}
    #janeSceneHost>[data-jane-scene][data-jane-active="true"]{pointer-events:auto!important;visibility:visible!important;opacity:1!important}
    .monolith-scene-root{position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;overflow:hidden!important;background:#01050a!important}
    .monolith-scene-root[data-jane-active="false"]{display:none!important}
    .monolith-scene-root[data-jane-active="true"]{display:block!important}
    #monolithModuleOverlay{display:none!important;pointer-events:none!important}
  `;
  document.head.appendChild(style);

  const observer = new MutationObserver(() => {
    purgeLegacyLayers();
    if (window.JaneSceneRouter) installRouter();
    if (sceneHost()) ensureExternalScenes();
  });
  observer.observe(document.documentElement, { childList: true, subtree: true });

  window.MonolithSceneRuntime = {
    version: VERSION,
    refresh() {
      ensureCommandDeck();
      purgeLegacyLayers();
      installRouter();
      ensureExternalScenes();
      window.MonolithFinalUi?.refresh?.();
      return true;
    },
    routeFor(name) {
      return routeName(name);
    },
    sceneFor(name) {
      const route = routeName(name);
      return externalScenes.get(route) || ensureExternalScene(route);
    },
    open(name, options) {
      if (!installRouter()) return false;
      return routedRouter.open(routeName(name), options);
    },
    back() {
      if (!installRouter()) return false;
      return routedRouter.back();
    }
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", ensureCommandDeck, { once: true });
  } else {
    ensureCommandDeck();
  }
})();
