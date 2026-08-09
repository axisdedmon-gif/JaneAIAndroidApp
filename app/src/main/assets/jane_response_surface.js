/* Keep Jane's complete response text reliably touch-scrollable on Android. */
(function(){
  const FLAG="data-jane-response-scroll";

  function install(){
    const route=document.getElementById("vn")||document.querySelector(".vn");
    const body=document.getElementById("dialogText");
    const dialog=route&&route.querySelector(".dialog");
    if(!route||!body||!dialog)return;

    body.setAttribute(FLAG,"ready");
    body.setAttribute("tabindex","0");
    body.style.setProperty("overflow-y","scroll","important");
    body.style.setProperty("overflow-x","hidden","important");
    body.style.setProperty("touch-action","pan-y","important");
    body.style.setProperty("-webkit-overflow-scrolling","touch","important");
    body.style.setProperty("overscroll-behavior-y","contain","important");
    body.style.setProperty("pointer-events","auto","important");

    dialog.style.setProperty("display","flex","important");
    dialog.style.setProperty("flex-direction","column","important");
    dialog.style.setProperty("overflow","hidden","important");

    if(!body.__janeResponseTouchBound){
      body.__janeResponseTouchBound=true;
      // Keep vertical gestures inside the response text instead of allowing an
      // ancestor overlay to steal them. Passive listeners preserve native scroll.
      body.addEventListener("touchstart",function(event){event.stopPropagation();},{passive:true});
      body.addEventListener("touchmove",function(event){event.stopPropagation();},{passive:true});
      body.addEventListener("wheel",function(event){event.stopPropagation();},{passive:true});
    }
  }

  function resetToTop(){
    install();
    const body=document.getElementById("dialogText");
    if(body)requestAnimationFrame(function(){body.scrollTop=0;});
  }

  const body=document.getElementById("dialogText");
  if(body){
    new MutationObserver(function(){resetToTop();}).observe(body,{childList:true,characterData:true,subtree:true});
  }

  const route=document.getElementById("vn")||document.querySelector(".vn");
  if(route){
    new MutationObserver(function(){install();}).observe(route,{attributes:true,attributeFilter:["class"]});
  }

  document.addEventListener("DOMContentLoaded",install,{once:true});
  window.addEventListener("load",install,{once:true});
  install();

  window.JANE_RESPONSE_SURFACE={
    personalityFirstOfflineAI:true,
    exactListRecovery:true,
    knowledgeBound:true,
    sourcesOnlyWhenAsked:true,
    quotesOnlyWhenAsked:true,
    fullTouchScrollableResponses:true,
    archiveStorageUntouched:true
  };
})();
