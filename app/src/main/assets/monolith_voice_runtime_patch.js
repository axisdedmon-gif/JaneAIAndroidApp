(function(){
  "use strict";
  if(window.MonolithVoiceRuntimePatch)return;

  function parseState(){
    try{return window.AndroidMonolith?JSON.parse(AndroidMonolith.getVoiceWorkspace()||"{}"):{};}catch(_){return {};}
  }

  function apply(){
    const overlay=document.getElementById("monolithModuleOverlay");
    if(!overlay||overlay.hidden)return;
    const voicePanel=overlay.querySelector(".monolith-voice-grid");
    if(!voicePanel)return;
    const state=parseState();
    const models=new Map((state.models||[]).map(m=>[String(m.id),m]));

    overlay.querySelectorAll("[data-voice-model]").forEach(btn=>{
      const row=models.get(String(btn.dataset.voiceModel));
      if(!row)return;
      btn.disabled=!row.runnable;
      if(row.active)btn.textContent="ACTIVE // LOCAL";
      else if(row.runnable)btn.textContent="ACTIVATE LOCAL";
      else btn.textContent="CONVERSION REQUIRED";
    });

    const datasets=state.datasets||[];
    const datasetList=voicePanel.querySelector(".voice-dataset .voice-list");
    if(datasetList){
      Array.from(datasetList.querySelectorAll("article")).forEach(article=>{
        const id=(article.querySelector("strong")?.textContent||"").trim();
        const data=datasets.find(d=>String(d.id)===id);
        if(!data||article.querySelector("[data-export-dataset]"))return;
        const btn=document.createElement("button");
        btn.type="button";
        btn.dataset.exportDataset=id;
        btn.textContent=data.exportable?"EXPORT DATASET ZIP":"DATASET INCOMPLETE";
        btn.disabled=!data.exportable;
        btn.addEventListener("click",()=>{
          try{AndroidMonolith.exportVoiceDataset(id);}catch(_){window.MonolithVoiceNotice?.("ERROR:Dataset export bridge unavailable.");}
        });
        article.appendChild(btn);
      });
    }

    const reactor=voicePanel.querySelector(".voice-reactor em");
    if(reactor){
      const active=state.activeModel?`LOCAL MODEL // ${state.activeModel}`:"LOCAL MODEL SLOT // EMPTY";
      reactor.textContent=`${active} // ${String(state.runtimeState||"inactive").toUpperCase()}`;
    }
    const framework=voicePanel.querySelector(".voice-framework small");
    if(framework)framework.textContent="ON-DEVICE DATASET CAPTURE // PIPER OFFLINE TRAINING // SHERPA CONVERSION // LOCAL SYSTEMIC SPEECH";
  }

  window.MonolithVoiceNotice=function(message){
    const target=document.querySelector("#monolithModuleOverlay #voiceRecordState");
    if(target)target.textContent=String(message||"");
  };

  const observer=new MutationObserver(()=>apply());
  observer.observe(document.documentElement,{childList:true,subtree:true});
  window.MonolithVoiceRuntimePatch={apply};
  setInterval(apply,1200);
  apply();
})();
