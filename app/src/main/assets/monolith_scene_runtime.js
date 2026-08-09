(function(){
  "use strict";
  if(window.MonolithSceneRuntime)return;

  const MODULE_ROUTES={model:"monolith-model",voice:"monolith-voice",rpg:"monolith-rpg"};
  let installed=false;
  let baseRouter=null;
  let routedRouter=null;
  let originalCoreOpen=null;
  let activeExternal="";
  let returnScene="command";
  const externalScenes=new Map();

  function loadStyle(id,href){
    if(document.getElementById(id))return;
    const link=document.createElement("link");
    link.id=id;
    link.rel="stylesheet";
    link.href=href;
    document.head.appendChild(link);
  }

  function loadScript(id,src,onload){
    const existing=document.getElementById(id);
    if(existing){
      if(onload)setTimeout(onload,0);
      return;
    }
    const script=document.createElement("script");
    script.id=id;
    script.src=src;
    if(onload)script.addEventListener("load",onload,{once:true});
    document.head.appendChild(script);
  }

  function purgeLegacyLayers(){
    document.getElementById("ownerGate")?.remove();
    document.getElementById("janeVitalsHUD")?.remove();
    document.querySelectorAll(".jane-vitals-column,.jane-vital-card").forEach(node=>node.remove());
  }

  function sceneHost(){return document.getElementById("janeSceneHost");}

  function setSceneVisible(scene,visible){
    if(!scene)return;
    scene.dataset.janeActive=visible?"true":"false";
    scene.setAttribute("aria-hidden",visible?"false":"true");
  }

  function ensureExternalScenes(){
    const host=sceneHost();
    if(!host)return false;
    Object.values(MODULE_ROUTES).forEach(route=>{
      let scene=document.querySelector(`[data-jane-scene="${route}"]`);
      if(!scene){
        scene=document.createElement("section");
        scene.className="monolith-module-scene";
        scene.dataset.janeScene=route;
        scene.dataset.janeActive="false";
        scene.setAttribute("aria-hidden","true");
        host.appendChild(scene);
      }
      externalScenes.set(route,scene);
    });
    return true;
  }

  function hideExternalScenes(){
    externalScenes.forEach(scene=>setSceneVisible(scene,false));
    const overlay=document.getElementById("monolithModuleOverlay");
    if(overlay)overlay.hidden=true;
  }

  function installRouter(){
    if(routedRouter&&window.JaneSceneRouter===routedRouter)return true;
    if(!window.JaneSceneRouter||typeof window.JaneSceneRouter.open!=="function")return false;
    if(!baseRouter||window.JaneSceneRouter!==routedRouter)baseRouter=window.JaneSceneRouter;
    if(!ensureExternalScenes())return false;

    routedRouter={
      open(name,options){
        const route=String(name||"");
        if(externalScenes.has(route)){
          const host=sceneHost();
          if(!host)return false;
          if(!activeExternal){
            const prior=typeof baseRouter.current==="function"?baseRouter.current():"command";
            if(prior&&!externalScenes.has(prior))returnScene=prior;
          }
          host.querySelectorAll(":scope > [data-jane-scene]").forEach(scene=>setSceneVisible(scene,scene.dataset.janeScene===route));
          activeExternal=route;
          window.dispatchEvent(new CustomEvent("jane-scene-change",{detail:{scene:route}}));
          return true;
        }
        activeExternal="";
        hideExternalScenes();
        return baseRouter.open(route,options);
      },
      back(){
        if(activeExternal){
          const target=returnScene||"command";
          activeExternal="";
          hideExternalScenes();
          return baseRouter.open(target);
        }
        return typeof baseRouter.back==="function"?baseRouter.back():baseRouter.open("command");
      },
      current(){return activeExternal||(typeof baseRouter.current==="function"?baseRouter.current():"command");},
      register(name,element){
        if(!name||!element||!sceneHost())return false;
        element.dataset.janeScene=name;
        element.dataset.janeActive="false";
        element.setAttribute("aria-hidden","true");
        sceneHost().appendChild(element);
        externalScenes.set(name,element);
        return true;
      }
    };
    window.JaneSceneRouter=routedRouter;
    return true;
  }

  function normalizeReturnButton(overlay){
    const old=overlay?.querySelector(".monolith-return");
    if(!old||old.dataset.sceneReturn==="true")return;
    const clean=old.cloneNode(true);
    clean.dataset.sceneReturn="true";
    clean.addEventListener("click",event=>{
      event.preventDefault();
      event.stopImmediatePropagation();
      window.JaneSceneRouter?.back?.();
    },true);
    old.replaceWith(clean);
  }

  function dockRenderedModule(name){
    const route=MODULE_ROUTES[name];
    const overlay=document.getElementById("monolithModuleOverlay");
    const target=externalScenes.get(route);
    if(!route||!overlay||!target)return false;
    overlay.classList.add("monolith-routed-surface");
    overlay.hidden=false;
    target.appendChild(overlay);
    normalizeReturnButton(overlay);
    window.JaneSceneRouter.open(route);
    return true;
  }

  function openModule(name){
    if(!originalCoreOpen||!MODULE_ROUTES[name])return;
    installRouter();
    originalCoreOpen(name);
    dockRenderedModule(name);
  }

  function wireModuleMenu(){
    const bindings=[
      ["monolithNavModel","model"],
      ["monolithNavVoice","voice"],
      ["monolithNavRpg","rpg"]
    ];
    bindings.forEach(([id,name])=>{
      const button=document.getElementById(id);
      if(!button||button.dataset.sceneWired==="true")return;
      button.dataset.sceneWired="true";
      button.onclick=event=>{
        event.preventDefault();
        openModule(name);
      };
    });
  }

  function patchMonolithCore(){
    if(!window.MonolithCore||typeof window.MonolithCore.open!=="function")return false;
    if(!originalCoreOpen)originalCoreOpen=window.MonolithCore.open.bind(window.MonolithCore);
    window.MonolithCore.open=openModule;
    window.MonolithCore.close=()=>window.JaneSceneRouter?.back?.();
    wireModuleMenu();
    return true;
  }

  function finishDeck(){
    purgeLegacyLayers();
    if(!window.JaneSceneRouter){setTimeout(finishDeck,40);return;}
    installRouter();
    ensureExternalScenes();
    patchMonolithCore();
    window.JaneSceneRouter.open("command");
    window.JaneQolHud?.refresh?.();
    installed=true;
  }

  function ensureCommandDeck(){
    loadStyle("jane-command-deck-css","file:///android_asset/jane_command_deck.css");
    if(window.JaneSceneRouter){finishDeck();return;}
    loadScript("jane-command-deck-js","file:///android_asset/jane_command_deck.js",finishDeck);
  }

  const style=document.createElement("style");
  style.id="monolith-scene-runtime-css";
  style.textContent=`
    body.jane-command-deck #ownerGate,
    body.jane-command-deck #janeVitalsHUD,
    body.jane-command-deck .jane-vitals-column,
    body.jane-command-deck .jane-vital-card{display:none!important}
    .monolith-module-scene{position:relative;min-height:100%;width:100%;box-sizing:border-box;overflow:auto;background:#010307}
    .monolith-module-scene[data-jane-active="false"]{display:none!important}
    .monolith-module-scene[data-jane-active="true"]{display:block!important}
    #monolithModuleOverlay.monolith-routed-surface{position:relative!important;inset:auto!important;z-index:auto!important;width:100%!important;min-height:100%!important;box-sizing:border-box!important;overflow:visible!important;padding:12px!important;background:radial-gradient(circle at 50% 35%,rgba(38,104,255,.12),transparent 38%),#010307!important}
  `;
  document.head.appendChild(style);

  const observer=new MutationObserver(()=>{
    purgeLegacyLayers();
    if(window.JaneSceneRouter)installRouter();
    if(window.MonolithCore)patchMonolithCore();
    wireModuleMenu();
    const overlay=document.getElementById("monolithModuleOverlay");
    if(overlay&&activeExternal){
      normalizeReturnButton(overlay);
      const target=externalScenes.get(activeExternal);
      if(target&&overlay.parentNode!==target)target.appendChild(overlay);
    }
  });
  observer.observe(document.documentElement,{childList:true,subtree:true});

  window.MonolithSceneRuntime={
    refresh(){ensureCommandDeck();purgeLegacyLayers();installRouter();patchMonolithCore();wireModuleMenu();},
    openModule
  };

  if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",ensureCommandDeck,{once:true});
  else ensureCommandDeck();
})();
