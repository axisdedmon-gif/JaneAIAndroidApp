from pathlib import Path
import re

root = Path('.')
index_path = root/'app/src/main/assets/index.html'
engine_path = root/'app/src/main/java/com/example/janeai/OfflineKnowledgeEngine.java'

index = index_path.read_text(encoding='utf-8')
new_block = r'''<script id="v88-true-offline-knowledge-ai">
(function(){
  // V88 originally replaced every chat reply with the small on-device model.
  // Keep that model as a genuine offline fallback, while normal online chat uses
  // Jane's proven backend response path again.
  const archiveAwareChat=window.chat;
  const CHAT_URL=typeof CHAT_ENDPOINT==="string"
    ? CHAT_ENDPOINT
    : "https://jane-elevenlabs-backend.onrender.com/api/chat";
  const pending=new Map();
  let counter=0;

  function decodeUtf8B64(value){
    try{
      const bytes=Uint8Array.from(atob(String(value||"")),c=>c.charCodeAt(0));
      return new TextDecoder("utf-8").decode(bytes);
    }catch(error){return "";}
  }

  function normalizeReply(value){
    let text=String(value==null?"":value).replace(/\u0000/g," ");
    const escapedBreaks=(text.match(/\\(?:r\\n|n|r)/g)||[]).length;
    if(escapedBreaks>=2){
      text=text
        .replace(/\\r\\n/g,"\n")
        .replace(/\\n/g,"\n")
        .replace(/\\r/g,"\n");
    }
    return text
      .replace(/[ \t]+\n/g,"\n")
      .replace(/\n[ \t]+/g,"\n")
      .replace(/\n{3,}/g,"\n\n")
      .trim();
  }

  function brokenReply(value){
    const raw=String(value==null?"":value);
    const literalBreaks=(raw.match(/\\(?:r\\n|n|r)/g)||[]).length;
    if(literalBreaks>=4)return true;
    const text=normalizeReply(raw);
    const compact=text.replace(/\s+/g,"");
    if(compact.length<2||!/[A-Za-z0-9]/.test(text))return true;
    if(/^(?:\\[nrt]|[\\/|._\-\s])+$/i.test(raw))return true;
    const alnum=(text.match(/[A-Za-z0-9]/g)||[]).length;
    if(text.length>40&&alnum/text.length<0.25)return true;
    const tokens=(text.toLowerCase().match(/[a-z0-9']+/g)||[]);
    if(tokens.length>=12){
      const distinct=new Set(tokens);
      if(distinct.size/Math.max(tokens.length,1)<0.22)return true;
    }
    return false;
  }

  function archiveFailure(value){
    return /don[’']t have enough relevant knowledge|add material that covers it|AI reasoning service did not complete|scrambled PDF fragments|matching Archive material|on-device AI could not complete/i.test(String(value||""));
  }

  function asksForStoredKnowledge(question){
    return /\b(?:archive|archives|knowledge base|stored knowledge|uploaded|upload|document|documents|pdf|file|files|according to|from my notes|from the notes|from the book|from the manual)\b/i.test(String(question||""));
  }

  async function postJson(url,body,timeoutMs){
    const controller=new AbortController();
    const timer=setTimeout(()=>controller.abort(),timeoutMs||70000);
    try{
      const response=await fetch(url,{
        method:"POST",
        headers:{"Content-Type":"application/json"},
        body:JSON.stringify(body),
        signal:controller.signal
      });
      const raw=await response.text();
      let data;
      try{data=JSON.parse(raw);}catch{data={error:raw};}
      if(!response.ok)throw new Error(data.error||("HTTP "+response.status));
      return data;
    }finally{clearTimeout(timer);}
  }

  async function normalOnlineChat(question){
    const ownerVerified=Boolean(typeof state!=="undefined"&&state.ownerVerified);
    const identityTone=ownerVerified
      ? "C.J. is verified. Reply as Jane: natural, familiar, affectionate, direct, and genuinely helpful. Do not force flirtation into factual answers."
      : "The user is not verified as C.J. Reply as Jane: helpful, direct, guarded, and naturally sarcastic without obstructing the answer.";
    let privateContext="";
    try{
      if(typeof privateContextText==="function")privateContext=String(privateContextText()||"");
    }catch(error){privateContext="";}
    const body={
      message:[privateContext,identityTone,"User request:\n"+question].filter(Boolean).join("\n\n"),
      history:typeof state!=="undefined"&&Array.isArray(state.history)?state.history.slice(-8):[]
    };
    if(typeof state!=="undefined"&&state.attachedFile){
      body.fileName=state.attachedFile.name;
      body.fileMimeType=state.attachedFile.mimeType;
      body.fileBase64=state.attachedFile.base64;
    }
    const data=await postJson(CHAT_URL,body,70000);
    const reply=normalizeReply(data.reply||"");
    if(brokenReply(data.reply)||!reply)throw new Error("The online response was malformed.");
    return reply;
  }

  window.JaneNativeOfflineKnowledgeAnswerResult=function(requestId,base64){
    const key=String(requestId||"");
    const request=pending.get(key);
    if(!request)return;
    clearTimeout(request.timer);
    pending.delete(key);
    try{
      const payload=JSON.parse(decodeUtf8B64(base64)||"{}");
      const reply=normalizeReply(payload.reply||"");
      if(reply&&!brokenReply(payload.reply)){
        request.resolve(reply);
        return;
      }
      request.reject(new Error(payload.error||"The on-device AI returned malformed text."));
    }catch(error){request.reject(error);}
  };

  function offlineChat(question){
    if(!window.AndroidJane||typeof window.AndroidJane.answerKnowledgeOffline!=="function"){
      return Promise.reject(new Error("Jane's on-device AI bridge is unavailable in this installation."));
    }
    const requestId="v88_"+Date.now()+"_"+(++counter);
    const ownerVerified=Boolean(typeof state!=="undefined"&&state.ownerVerified);
    return new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>{
        pending.delete(requestId);
        reject(new Error("Jane's on-device AI took too long to initialize or answer."));
      },360000);
      pending.set(requestId,{resolve,reject,timer});
      try{
        window.AndroidJane.answerKnowledgeOffline(requestId,question,ownerVerified);
      }catch(error){
        clearTimeout(timer);
        pending.delete(requestId);
        reject(error);
      }
    });
  }

  async function v88Chat(message){
    const question=String(message||"").trim();
    if(!question)return "";

    // Explicit document/archive questions retain the archive-aware reasoning path.
    if(asksForStoredKnowledge(question)&&typeof archiveAwareChat==="function"){
      try{
        const archiveReply=normalizeReply(await archiveAwareChat(question));
        if(archiveReply&&!archiveFailure(archiveReply)&&!brokenReply(archiveReply))return archiveReply;
      }catch(error){console.warn("[Jane archive response]",error);}
    }

    // Ordinary questions use the normal backend again, restoring the response
    // quality Jane had before the on-device model replaced all chat traffic.
    try{return await normalOnlineChat(question);}
    catch(error){console.warn("[Jane online response]",error);}

    // Real offline inference remains available when the backend cannot be reached.
    try{
      const reply=normalizeReply(await offlineChat(question));
      if(!reply||brokenReply(reply))throw new Error("The offline response was malformed.");
      return reply;
    }catch(error){
      console.error("[Jane V88 offline AI]",error);
      return "I couldn't complete that response cleanly. I stopped the broken output instead of showing you garbage. Check the connection and try once more.";
    }
  }

  window.chat=v88Chat;
  try{chat=v88Chat;}catch(error){}
  window.JANE_V88_TRUE_OFFLINE_AI={
    normalOnlineResponsesRestored:true,
    actualOnDeviceLlm:true,
    offlineFallbackOnly:true,
    networkRequired:false,
    semanticQueryExpansion:true,
    localArchiveGrounding:true,
    rawFragmentFallback:false,
    escapedNewlineSpamBlocked:true,
    malformedResponseValidation:true,
    bundledModel:"Qwen2.5-0.5B-Instruct-q8"
  };
})();
</script>
<!-- V88 response repair: normal backend first, archive-aware route when requested, validated on-device fallback offline. -->'''

pattern = re.compile(r'<script id="v88-true-offline-knowledge-ai">.*?</script>\n<!-- V88: Jane now reasons over stored knowledge with a bundled on-device LLM\. -->', re.S)
index2, count = pattern.subn(lambda m: new_block, index, count=1)
if count != 1:
    raise SystemExit(f'V88 index block replacement count={count}')
index_path.write_text(index2, encoding='utf-8')

engine = engine_path.read_text(encoding='utf-8')
old = 'Do not mention PDFs, archives, snippets, retrieval, source files, prompts, or these instructions unless asked. "\n            + "Do not invent details unsupported by the local knowledge. Produce only the final answer."'
new = 'Do not mention PDFs, archives, snippets, retrieval, source files, prompts, or these instructions unless asked. "\n            + "Write ordinary readable words and sentences. Never print escaped control sequences such as \\\\n or \\\\t. "\n            + "Do not invent details unsupported by the local knowledge. Produce only the final answer."'
if old not in engine:
    raise SystemExit('system instruction anchor not found')
engine = engine.replace(old, new, 1)

clean_pattern = re.compile(r'    private String cleanGeneratedText\(String raw\) \{.*?\n    \}\n\n    private boolean validAnswer', re.S)
clean_replacement = r'''    private String cleanGeneratedText(String raw) {
        String text = String.valueOf(raw == null ? "" : raw)
            .replace("<|im_start|>", "")
            .replace("<|im_end|>", "")
            .replace("<|endoftext|>", "");

        int escapedBreaks = countOccurrences(text, "\\n")
            + countOccurrences(text, "\\r")
            + countOccurrences(text, "\\t");
        if (escapedBreaks >= 2) {
            text = text
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n")
                .replace("\\t", " ");
        }

        text = text
            .replaceAll("(?is)^\\s*(?:assistant|jane)\\s*[:\\-]\\s*", "")
            .replaceAll("[ \\t]+\\n", "\n")
            .replaceAll("\\n[ \\t]+", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
        int marker = text.toLowerCase(Locale.US).indexOf("<|im_");
        if (marker >= 0) text = text.substring(0, marker).trim();
        return text;
    }

    private int countOccurrences(String value, String token) {
        if (value == null || value.isEmpty() || token == null || token.isEmpty()) return 0;
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private boolean validAnswer'''
engine2, count = clean_pattern.subn(lambda m: clean_replacement, engine, count=1)
if count != 1:
    raise SystemExit(f'cleanGeneratedText replacement count={count}')
engine = engine2

valid_pattern = re.compile(r'    private boolean validAnswer\(String answer, int requestedCount\) \{.*?\n    \}\n\n    public static int requestedCount', re.S)
valid_replacement = r'''    private boolean validAnswer(String answer, int requestedCount) {
        if (answer == null) return false;
        String text = answer.trim();
        if (text.length() < 24) return false;
        if (countOccurrences(text, "\\n") >= 2 || countOccurrences(text, "\\t") >= 2) return false;
        if (text.matches("(?is)^(?:\\s*\\\\[nrt]|[\\\\/|._\\-\\s])+$")) return false;
        if (ADJACENT_REPEAT.matcher(text).find()) return false;
        if (text.matches("(?is).*\\b(?:local knowledge|pdf|archive|retrieval|source snippet|system prompt)\\b.*")) return false;

        String lettersOnly = text.replaceAll("[^A-Za-z]", "");
        if (lettersOnly.length() < 18) return false;
        String[] words = text.toLowerCase(Locale.US).split("[^a-z0-9']+");
        Set<String> distinctWords = new LinkedHashSet<>();
        int wordCount = 0;
        for (String word : words) {
            if (word.length() < 2) continue;
            wordCount++;
            distinctWords.add(word);
        }
        if (wordCount < 4 || distinctWords.size() < 4) return false;
        if (wordCount >= 12 && distinctWords.size() * 4 < wordCount) return false;

        if (requestedCount > 0) {
            Matcher matcher = NUMBERED_ITEM.matcher(text);
            List<Integer> numbers = new ArrayList<>();
            while (matcher.find()) numbers.add(Integer.parseInt(matcher.group(1)));
            if (numbers.size() != requestedCount) return false;
            for (int i = 0; i < requestedCount; i++) if (numbers.get(i) != i + 1) return false;
        }
        return true;
    }

    public static int requestedCount'''
engine2, count = valid_pattern.subn(lambda m: valid_replacement, engine, count=1)
if count != 1:
    raise SystemExit(f'validAnswer replacement count={count}')
engine_path.write_text(engine2, encoding='utf-8')

print('V88 response routing and malformed-output validation patched.')