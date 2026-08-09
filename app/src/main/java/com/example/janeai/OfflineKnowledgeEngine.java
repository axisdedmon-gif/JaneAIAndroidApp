package com.example.janeai;

import android.content.Context;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * On-device knowledge responder.
 *
 * Retrieval is performed locally by MainActivity from Jane's preserved native
 * Archives. This class uses the bundled on-device language model only to reason
 * over and rewrite that retrieved knowledge in Jane's conversational voice.
 * No network service is required and no Archive files are modified here.
 */
public final class OfflineKnowledgeEngine implements AutoCloseable {
    private static final String MODEL_ASSET = "offline_ai/qwen2_5_0_5b_q8.task";
    private static final String MODEL_FILE = "qwen2_5_0_5b_q8.task";
    private static final long EXPECTED_MODEL_BYTES = 546_660_344L;
    private static final int MODEL_CONTEXT_TOKENS = 1280;
    private static final int MAX_PROMPT_TOKENS = 930;
    private static final int MAX_CONTEXT_CHARS = 5_400;

    private static final Pattern REQUESTED_COUNT = Pattern.compile(
        "\\b(\\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten)\\b(?=[\\s\\S]{0,90}\\b(?:facts?|reasons?|points?|ideas?|examples?|things?|basics?|rules?|steps?|ways?|must[- ]?knows?)\\b)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NUMBERED_ITEM = Pattern.compile(
        "(?m)^\\s*(?:\\*\\*)?(\\d{1,2})[.)]?(?:\\*\\*)?\\s*(?:[-:])?\\s+\\S"
    );
    private static final Pattern ADJACENT_REPEAT = Pattern.compile("(?i)\\b([a-z]{3,})\\s+\\1\\b");
    private static final Pattern SOURCE_REQUEST = Pattern.compile(
        "(?i)\\b(source|sources|citation|citations|cite|where did (?:you|that) get|which (?:book|file|document|pdf)|page number|what page)\\b"
    );
    private static final Pattern QUOTE_REQUEST = Pattern.compile(
        "(?i)\\b(quote|quotation|exact words?|verbatim|word for word|passage)\\b"
    );

    private static volatile OfflineKnowledgeEngine instance;

    private final Context appContext;
    private final Object inferenceLock = new Object();
    private LlmInference inference;

    private OfflineKnowledgeEngine(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static OfflineKnowledgeEngine getInstance(Context context) {
        OfflineKnowledgeEngine current = instance;
        if (current != null) return current;
        synchronized (OfflineKnowledgeEngine.class) {
            if (instance == null) instance = new OfflineKnowledgeEngine(context);
            return instance;
        }
    }

    /**
     * Uses the bundled model to add semantic retrieval terms. The original
     * question is always searched too, so expansion cannot erase user intent.
     */
    public String expandSearchQuery(String question) throws Exception {
        String cleanQuestion = safeText(question, 900);
        if (cleanQuestion.isEmpty()) return "";
        String system = "Create search terms for a private offline document library. "
            + "Return only 6 to 12 concise keywords or short phrases separated by commas. "
            + "Include useful synonyms and closely related concepts. Do not answer the question.";
        String raw = generate(qwenPrompt(system, cleanQuestion));
        String expansion = cleanGeneratedText(raw)
            .replaceAll("(?i)^(?:keywords?|search terms?|query)\\s*[:\\-]\\s*", "")
            .replaceAll("[\\n;|]+", ", ")
            .replaceAll("\\s+", " ")
            .trim();
        if (expansion.length() > 520) expansion = expansion.substring(0, 520);
        return expansion;
    }

    public String answer(String question, JSONArray hits, boolean ownerVerified) throws Exception {
        String cleanQuestion = safeText(question, 2_000);
        if (cleanQuestion.isEmpty()) return "What would you like me to answer?";

        int requestedCount = requestedCount(cleanQuestion);
        boolean wantsSources = SOURCE_REQUEST.matcher(cleanQuestion).find();
        boolean wantsQuote = QUOTE_REQUEST.matcher(cleanQuestion).find();
        String context = buildContext(hits, wantsSources || wantsQuote);

        // Jane's knowledge mode is intentionally bounded by what C.J. has taught her.
        if (context.isEmpty()) {
            return "I don't know that from the knowledge you've given me yet.";
        }

        String system = buildSystemInstruction(
            ownerVerified,
            requestedCount,
            wantsSources,
            wantsQuote
        );
        String user = "QUESTION:\n" + cleanQuestion + "\n\nPRIVATE KNOWLEDGE:\n" + context;

        String prompt = fitPrompt(system, user, context, cleanQuestion);
        String firstDraft = cleanGeneratedText(generate(prompt));
        String answer = firstDraft;

        if (!validAnswer(answer, requestedCount, wantsSources, wantsQuote)) {
            String repairSystem = system
                + " Rewrite the draft completely. Keep the facts, but make the response sound like Jane speaking naturally to a person. "
                + "Do not explain the repair. Do not use broken fragments or textbook-style lead-ins."
                + (requestedCount > 0
                    ? " The final response MUST contain exactly " + requestedCount + " numbered items and no extra numbered items."
                    : "");
            String repairUser = "QUESTION:\n" + cleanQuestion
                + "\n\nPRIVATE KNOWLEDGE:\n" + context
                + "\n\nDRAFT TO REWRITE:\n" + safeText(firstDraft, 1_500);
            answer = cleanGeneratedText(generate(fitPrompt(repairSystem, repairUser, context, cleanQuestion)));
        }

        if (requestedCount > 0 && !hasExactRequestedCount(answer, requestedCount)) {
            String coerced = coerceNumberedAnswer(answer, requestedCount);
            if (coerced == null) coerced = coerceNumberedAnswer(firstDraft, requestedCount);
            if (coerced != null) answer = coerced;
        }

        answer = finalPolish(answer, wantsSources, wantsQuote);

        if (!validAnswer(answer, requestedCount, wantsSources, wantsQuote)) {
            // A meaningful AI draft is better than a false "I couldn't answer" failure.
            String salvaged = finalPolish(firstDraft, wantsSources, wantsQuote);
            if (requestedCount > 0 && !hasExactRequestedCount(salvaged, requestedCount)) {
                String coerced = coerceNumberedAnswer(salvaged, requestedCount);
                if (coerced != null) salvaged = coerced;
            }
            if (isMeaningfulProse(salvaged)
                    && (requestedCount == 0 || hasExactRequestedCount(salvaged, requestedCount))) {
                return salvaged;
            }
            throw new IllegalStateException("Jane's on-device model did not produce a usable response.");
        }
        return answer;
    }

    private String buildSystemInstruction(
            boolean ownerVerified,
            int requestedCount,
            boolean wantsSources,
            boolean wantsQuote) {
        StringBuilder out = new StringBuilder();
        out.append("You are Jane, C.J.'s personal AI companion running completely offline. ");
        if (ownerVerified) {
            out.append("You know you are speaking directly to C.J. Be familiar, confident, intelligent, conversational, and lightly playful when it fits. ");
        } else {
            out.append("Be confident, intelligent, conversational, concise, and human-readable. ");
        }

        out.append("The PRIVATE KNOWLEDGE is memory Jane has been taught. Treat it as knowledge you already understand, not as a book you are reading aloud. ")
            .append("Reason over it, combine related facts, repair obvious OCR damage silently, and explain the answer freshly in your own words. ")
            .append("Never paste passages, stitch fragments together, or sound like a textbook, librarian, citation engine, or search result. ")
            .append("Never say phrases such as 'according to the text', 'the material says', 'the document states', 'from the book', or 'the source says'. ")
            .append("Lead with the actual answer. Use natural transitions. Keep details useful rather than padded. ")
            .append("Do not add factual claims that are not supported by the PRIVATE KNOWLEDGE. ");

        if (requestedCount > 0) {
            out.append("The user asked for exactly ").append(requestedCount)
                .append(" items. Give exactly ").append(requestedCount)
                .append(" distinct numbered items, numbered 1 through ").append(requestedCount)
                .append(", with each item written as a complete useful thought. ");
        }

        if (wantsSources) {
            out.append("The user explicitly asked for sources, so source names may be given briefly after the answer. ");
        } else {
            out.append("Do not mention filenames, PDFs, documents, archives, sources, citations, page numbers, retrieval, or snippets. ");
        }

        if (wantsQuote) {
            out.append("The user explicitly asked for exact wording, so a short verbatim quotation from the supplied knowledge is allowed. ");
        } else {
            out.append("Do not quote the supplied wording. Paraphrase it naturally. ");
        }

        out.append("Use ordinary readable sentences. Never print escaped control sequences. Produce only Jane's final response.");
        return out.toString();
    }

    private String fitPrompt(String system, String user, String context, String question) throws Exception {
        String prompt = qwenPrompt(system, user);
        synchronized (inferenceLock) {
            ensureInference();
            if (inference.sizeInTokens(prompt) <= MAX_PROMPT_TOKENS) return prompt;
        }

        String reducedContext = context;
        while (!reducedContext.isEmpty() && reducedContext.length() > 700) {
            reducedContext = reducedContext.substring(0, (int) (reducedContext.length() * 0.82));
            int boundary = Math.max(reducedContext.lastIndexOf(". "), reducedContext.lastIndexOf("\n"));
            if (boundary > reducedContext.length() / 2) {
                reducedContext = reducedContext.substring(0, boundary + 1);
            }
            String reducedUser = "QUESTION:\n" + question + "\n\nPRIVATE KNOWLEDGE:\n" + reducedContext;
            prompt = qwenPrompt(system, reducedUser);
            synchronized (inferenceLock) {
                ensureInference();
                if (inference.sizeInTokens(prompt) <= MAX_PROMPT_TOKENS) return prompt;
            }
        }

        String shortSystem = "You are Jane, C.J.'s offline AI companion. Answer in a natural conversational voice. "
            + "Use only the supplied private knowledge for facts. Understand and paraphrase it; never recite it or mention files. "
            + "If the question asks for a fixed number of items, return exactly that many numbered items.";
        String shortUser = "QUESTION:\n" + safeText(question, 900)
            + "\n\nPRIVATE KNOWLEDGE:\n" + safeText(reducedContext, 1_600);
        return qwenPrompt(shortSystem, shortUser);
    }

    private String buildContext(JSONArray hits, boolean includeSourceNames) {
        if (hits == null || hits.length() == 0) return "";
        StringBuilder out = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        int total = 0;
        int materialNumber = 0;

        for (int i = 0; i < hits.length() && i < 18; i++) {
            JSONObject hit = hits.optJSONObject(i);
            if (hit == null) continue;
            String source = cleanSourceName(hit.optString("source", "Stored knowledge"));
            String text = cleanKnowledgeText(hit.optString("text", ""));
            if (text.length() < 50) continue;

            String key = text.substring(0, Math.min(240, text.length())).toLowerCase(Locale.US);
            if (!seen.add(key)) continue;
            if (text.length() > 1_050) text = text.substring(0, 1_050);

            String prefix = includeSourceNames
                ? "[Memory " + (++materialNumber) + " | " + source + "] "
                : "[Memory " + (++materialNumber) + "] ";
            int addition = prefix.length() + text.length() + 2;
            if (total + addition > MAX_CONTEXT_CHARS) break;
            if (out.length() > 0) out.append("\n\n");
            out.append(prefix).append(text);
            total += addition;
        }
        return out.toString().trim();
    }

    private String cleanSourceName(String value) {
        return safeText(value, 120)
            .replaceAll("(?i)\\.(pdf|docx?|txt|md|rtf)$", "")
            .trim();
    }

    private String cleanKnowledgeText(String value) {
        String text = String.valueOf(value == null ? "" : value)
            .replace('\r', '\n')
            .replaceAll("([A-Za-z]{3,})-\\s*\\n\\s*([a-z]{2,})", "$1$2")
            .replaceAll("(?i)\\bthis page intentionally left blank\\b", " ")
            .replaceAll("(?i)\\b(?:copyright|isbn(?:-1[03])?)\\b[^\\n]{0,180}", " ")
            .replaceAll("(?i)\\btable of contents\\b", " ")
            .replaceAll("(?i)\\bdesigned for teaching\\b[^.]{0,220}\\.?", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return safeText(text, 2_100);
    }

    private String qwenPrompt(String system, String user) {
        return "<|im_start|>system\n" + system + "<|im_end|>\n"
            + "<|im_start|>user\n" + user + "<|im_end|>\n"
            + "<|im_start|>assistant\n";
    }

    private String generate(String prompt) throws Exception {
        synchronized (inferenceLock) {
            ensureInference();
            LlmInferenceSession.LlmInferenceSessionOptions sessionOptions =
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(30)
                    .setTemperature(0.36f)
                    .setRandomSeed(89)
                    .build();
            try (LlmInferenceSession session = LlmInferenceSession.createFromOptions(inference, sessionOptions)) {
                session.addQueryChunk(prompt);
                return session.generateResponse();
            }
        }
    }

    private void ensureInference() throws Exception {
        if (inference != null) return;
        File model = ensureModelFile();
        LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(model.getAbsolutePath())
            .setMaxTokens(MODEL_CONTEXT_TOKENS)
            .setMaxTopK(30)
            .build();
        inference = LlmInference.createFromOptions(appContext, options);
    }

    private File ensureModelFile() throws Exception {
        File directory = new File(appContext.getFilesDir(), "offline_ai");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create Jane's offline AI directory.");
        }
        File destination = new File(directory, MODEL_FILE);
        if (destination.exists() && destination.length() == EXPECTED_MODEL_BYTES) return destination;

        File temporary = new File(directory, MODEL_FILE + ".copying");
        if (temporary.exists() && !temporary.delete()) {
            throw new IllegalStateException("Could not reset the incomplete offline model copy.");
        }

        byte[] buffer = new byte[1024 * 1024];
        long copied = 0;
        try (InputStream input = appContext.getAssets().open(MODEL_ASSET);
             FileOutputStream output = new FileOutputStream(temporary)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                copied += read;
            }
            output.getFD().sync();
        }

        if (copied != EXPECTED_MODEL_BYTES) {
            temporary.delete();
            throw new IllegalStateException("The bundled offline AI model is incomplete.");
        }
        if (destination.exists() && !destination.delete()) {
            temporary.delete();
            throw new IllegalStateException("Could not replace the previous offline AI model.");
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IllegalStateException("Could not activate Jane's offline AI model.");
        }
        return destination;
    }

    private String cleanGeneratedText(String raw) {
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
            .replaceAll("(?i)^\\s*(?:according to|based on|from)\\s+(?:the|your)?\\s*(?:provided|local|private)?\\s*(?:text|material|knowledge|document|source|archive)[,:]?\\s*", "")
            .replaceAll("[ \\t]+\\n", "\n")
            .replaceAll("\\n[ \\t]+", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();

        int marker = text.toLowerCase(Locale.US).indexOf("<|im_");
        if (marker >= 0) text = text.substring(0, marker).trim();
        return text;
    }

    private String finalPolish(String answer, boolean wantsSources, boolean wantsQuote) {
        String text = cleanGeneratedText(answer);
        if (!wantsSources) {
            text = text
                .replaceAll("(?im)^\\s*(?:sources?|citations?)\\s*:\\s*.*$", "")
                .replaceAll("(?i)\\b(?:in|from|according to)\\s+(?:the\\s+)?(?:pdf|document|archive|source|book|textbook)\\b[^.!?]*[.!?]?", " ");
        }
        if (!wantsQuote) {
            text = text.replaceAll("(?m)^\\s*[>\"]\\s*", "");
        }
        return text
            .replaceAll("[ \\t]{2,}", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
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

    private boolean validAnswer(
            String answer,
            int requestedCount,
            boolean wantsSources,
            boolean wantsQuote) {
        if (!isMeaningfulProse(answer)) return false;
        String text = answer.trim();
        if (countOccurrences(text, "\\n") >= 2 || countOccurrences(text, "\\t") >= 2) return false;
        if (ADJACENT_REPEAT.matcher(text).find()) return false;
        if (!wantsSources && text.matches("(?is).*\\b(?:local knowledge|source snippet|system prompt|retrieval|archive material)\\b.*")) {
            return false;
        }
        if (!wantsQuote && text.matches("(?is).*\\b(?:the document states|the text says|the source says|according to the text)\\b.*")) {
            return false;
        }
        return requestedCount <= 0 || hasExactRequestedCount(text, requestedCount);
    }

    private boolean isMeaningfulProse(String answer) {
        if (answer == null) return false;
        String text = answer.trim();
        if (text.length() < 24) return false;
        if (text.matches("(?is)^(?:\\s*\\\\[nrt]|[\\\\/|._\\-\\s])+$")) return false;
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
        return wordCount < 12 || distinctWords.size() * 4 >= wordCount;
    }

    private boolean hasExactRequestedCount(String text, int requestedCount) {
        Matcher matcher = NUMBERED_ITEM.matcher(String.valueOf(text == null ? "" : text));
        List<Integer> numbers = new ArrayList<>();
        while (matcher.find()) numbers.add(Integer.parseInt(matcher.group(1)));
        if (numbers.size() != requestedCount) return false;
        for (int i = 0; i < requestedCount; i++) {
            if (numbers.get(i) != i + 1) return false;
        }
        return true;
    }

    private String coerceNumberedAnswer(String draft, int requestedCount) {
        if (requestedCount <= 0 || draft == null) return null;
        String clean = cleanGeneratedText(draft);
        if (clean.isEmpty()) return null;

        List<String> candidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        String[] lines = clean.split("\\n+");
        for (String line : lines) {
            String item = line
                .replaceFirst("^\\s*(?:[-•*]|(?:\\*\\*)?\\d{1,2}[.)]?(?:\\*\\*)?)\\s*(?:[-:])?\\s*", "")
                .trim();
            addCandidate(candidates, seen, item);
        }

        if (candidates.size() < requestedCount) {
            String[] sentences = clean.split("(?<=[.!?])\\s+");
            for (String sentence : sentences) addCandidate(candidates, seen, sentence.trim());
        }

        if (candidates.size() < requestedCount) return null;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < requestedCount; i++) {
            String item = candidates.get(i).trim();
            if (!item.matches(".*[.!?]$")) item += ".";
            if (i > 0) out.append('\n');
            out.append(i + 1).append(". ").append(item);
        }
        return out.toString();
    }

    private void addCandidate(List<String> candidates, Set<String> seen, String value) {
        String item = safeText(value, 700);
        if (item.length() < 20) return;
        String key = item.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").trim();
        if (key.length() < 16 || !seen.add(key)) return;
        candidates.add(item);
    }

    public static int requestedCount(String question) {
        Matcher matcher = REQUESTED_COUNT.matcher(String.valueOf(question == null ? "" : question));
        if (!matcher.find()) return 0;
        String value = matcher.group(1).toLowerCase(Locale.US);
        if (value.matches("\\d+")) return Math.max(1, Math.min(10, Integer.parseInt(value)));
        switch (value) {
            case "one": return 1;
            case "two": return 2;
            case "three": return 3;
            case "four": return 4;
            case "five": return 5;
            case "six": return 6;
            case "seven": return 7;
            case "eight": return 8;
            case "nine": return 9;
            case "ten": return 10;
            default: return 0;
        }
    }

    private String safeText(String value, int maxChars) {
        String text = String.valueOf(value == null ? "" : value)
            .replace('\u0000', ' ')
            .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ")
            .replaceAll("[ \\t]+", " ")
            .trim();
        return text.length() <= maxChars ? text : text.substring(0, maxChars).trim();
    }

    @Override
    public void close() {
        synchronized (inferenceLock) {
            if (inference != null) {
                inference.close();
                inference = null;
            }
        }
    }
}
