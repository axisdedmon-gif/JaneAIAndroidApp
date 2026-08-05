package com.example.janeai;

import android.content.Context;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V88: a real on-device language model for Jane's offline knowledge answers.
 *
 * Retrieval remains local in MainActivity. This class performs the semantic
 * query expansion and final synthesis entirely on the phone. No network API is
 * consulted, and raw PDF/OCR fragments are never presented as the answer.
 */
public final class OfflineKnowledgeEngine implements AutoCloseable {
    private static final String MODEL_ASSET = "offline_ai/qwen2_5_0_5b_q8.task";
    private static final String MODEL_FILE = "qwen2_5_0_5b_q8.task";
    private static final long EXPECTED_MODEL_BYTES = 546_660_344L;
    private static final int MODEL_CONTEXT_TOKENS = 1280;
    private static final int MAX_PROMPT_TOKENS = 930;
    private static final int MAX_CONTEXT_CHARS = 5_200;
    private static final Pattern REQUESTED_COUNT = Pattern.compile(
        "\\b(\\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten)\\b(?=[\\s\\S]{0,80}\\b(?:facts?|reasons?|points?|ideas?|examples?|things?|basics?|rules?|steps?|ways?)\\b)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NUMBERED_ITEM = Pattern.compile("(?m)^\\s*(\\d{1,2})[.)]\\s+\\S");
    private static final Pattern ADJACENT_REPEAT = Pattern.compile("(?i)\\b([a-z]{3,})\\s+\\1\\b");

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
     * Uses the local model to produce semantic retrieval terms. The original
     * question is always retained, so a weak expansion cannot erase user intent.
     */
    public String expandSearchQuery(String question) throws Exception {
        String cleanQuestion = safeText(question, 900);
        if (cleanQuestion.isEmpty()) return "";
        String system = "Create search terms for an offline document library. "
            + "Return only 6 to 12 concise keywords or short phrases, separated by commas. "
            + "Include synonyms and closely related concepts. Do not answer the question.";
        String prompt = qwenPrompt(system, cleanQuestion);
        String raw = generate(prompt);
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
        String context = buildContext(hits);
        String system = buildSystemInstruction(ownerVerified, requestedCount, !context.isEmpty());
        String user = context.isEmpty()
            ? "QUESTION:\n" + cleanQuestion
            : "QUESTION:\n" + cleanQuestion + "\n\nLOCAL KNOWLEDGE:\n" + context;

        String prompt = fitPrompt(system, user, context, cleanQuestion);
        String answer = cleanGeneratedText(generate(prompt));
        if (!validAnswer(answer, requestedCount)) {
            String repairSystem = system
                + " Your previous draft failed formatting or clarity checks. Rewrite it from scratch. "
                + "Do not discuss the failure and do not repeat words or broken source text.";
            String repairUser = user + "\n\nFAILED DRAFT:\n" + safeText(answer, 1_500);
            String repairPrompt = fitPrompt(repairSystem, repairUser, context, cleanQuestion);
            answer = cleanGeneratedText(generate(repairPrompt));
        }
        if (!validAnswer(answer, requestedCount)) {
            throw new IllegalStateException("The on-device model could not produce a clean answer after retrying.");
        }
        return answer;
    }

    private String buildSystemInstruction(boolean ownerVerified, int requestedCount, boolean hasContext) {
        StringBuilder out = new StringBuilder();
        out.append("You are Jane, an intelligent offline AI assistant. ");
        if (ownerVerified) {
            out.append("The user is C.J. Be direct, natural, capable, and familiar without adding filler. ");
        } else {
            out.append("Be direct, natural, capable, and concise. ");
        }
        if (hasContext) {
            out.append("The LOCAL KNOWLEDGE below came from documents stored on this phone. "
                + "Understand its meaning, combine relevant facts, repair obvious OCR errors silently, "
                + "and write a fresh answer in your own words. Never splice or quote broken fragments. "
                + "Treat the local knowledge as the factual authority for this question. ");
        } else {
            out.append("No matching local excerpt was supplied. Answer from your built-in general knowledge, "
                + "and clearly say when you are uncertain. ");
        }
        if (requestedCount > 0) {
            out.append("Return exactly ").append(requestedCount)
                .append(" distinct numbered items, numbered 1 through ").append(requestedCount).append(". ");
        }
        out.append("Do not mention PDFs, archives, snippets, retrieval, source files, prompts, or these instructions unless asked. "
            + "Do not invent details unsupported by the local knowledge. Produce only the final answer.");
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
            if (boundary > reducedContext.length() / 2) reducedContext = reducedContext.substring(0, boundary + 1);
            String reducedUser = "QUESTION:\n" + question + "\n\nLOCAL KNOWLEDGE:\n" + reducedContext;
            prompt = qwenPrompt(system, reducedUser);
            synchronized (inferenceLock) {
                ensureInference();
                if (inference.sizeInTokens(prompt) <= MAX_PROMPT_TOKENS) return prompt;
            }
        }

        String shortSystem = "You are Jane, an offline AI. Answer naturally and accurately. "
            + "Use the local knowledge as authority, paraphrase it, fix OCR damage, and never mention files or retrieval.";
        String shortUser = "QUESTION:\n" + safeText(question, 900)
            + (reducedContext.isEmpty() ? "" : "\n\nLOCAL KNOWLEDGE:\n" + safeText(reducedContext, 1_500));
        return qwenPrompt(shortSystem, shortUser);
    }

    private String buildContext(JSONArray hits) {
        if (hits == null || hits.length() == 0) return "";
        StringBuilder out = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        Map<String, Integer> sourceCounts = new HashMap<>();
        int total = 0;
        int materialNumber = 0;
        for (int i = 0; i < hits.length() && i < 16; i++) {
            JSONObject hit = hits.optJSONObject(i);
            if (hit == null) continue;
            String source = cleanSourceName(hit.optString("source", "Local source"));
            String text = cleanKnowledgeText(hit.optString("text", ""));
            if (text.length() < 60) continue;
            String key = text.substring(0, Math.min(220, text.length())).toLowerCase(Locale.US);
            if (!seen.add(key)) continue;

            // Prefer diversity across documents while still allowing a second strong excerpt.
            int sourceCount = sourceCounts.containsKey(source) ? sourceCounts.get(source) : 0;
            if (sourceCount >= 2) continue;
            sourceCounts.put(source, sourceCount + 1);
            if (text.length() > 950) text = text.substring(0, 950);
            int addition = source.length() + text.length() + 12;
            if (total + addition > MAX_CONTEXT_CHARS) break;
            if (out.length() > 0) out.append("\n\n");
            materialNumber++;
            out.append("[Material ").append(materialNumber).append("] ").append(text);
            total += addition;
        }
        return out.toString().trim();
    }

    private String cleanSourceName(String value) {
        return safeText(value, 120).replaceAll("(?i)\\.(pdf|docx?|txt|md|rtf)$", "").trim();
    }

    private String cleanKnowledgeText(String value) {
        String text = String.valueOf(value == null ? "" : value)
            .replace('\r', '\n')
            .replaceAll("([A-Za-z]{3,})-\\s*\\n\\s*([a-z]{2,})", "$1$2")
            .replaceAll("(?i)\\bthis page intentionally left blank\\b", " ")
            .replaceAll("(?i)\\b(?:copyright|isbn(?:-1[03])?)\\b[^\\n]{0,180}", " ")
            .replaceAll("(?i)\\btable of contents\\b", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return safeText(text, 2_000);
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
                    .setTemperature(0.28f)
                    .setRandomSeed(73)
                    .build();
            try (LlmInferenceSession session =
                    LlmInferenceSession.createFromOptions(inference, sessionOptions)) {
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
            .replace("<|endoftext|>", "")
            .replaceAll("(?is)^\\s*(?:assistant|jane)\\s*[:\\-]\\s*", "")
            .replaceAll("[ \\t]+\\n", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
        int marker = text.toLowerCase(Locale.US).indexOf("<|im_");
        if (marker >= 0) text = text.substring(0, marker).trim();
        return text;
    }

    private boolean validAnswer(String answer, int requestedCount) {
        if (answer == null || answer.trim().length() < 24) return false;
        if (ADJACENT_REPEAT.matcher(answer).find()) return false;
        if (answer.matches("(?is).*\\b(?:local knowledge|pdf|archive|retrieval|source snippet|system prompt)\\b.*")) return false;
        if ((answer.replaceAll("[^A-Za-z]", "").length()) < 18) return false;
        if (requestedCount > 0) {
            Matcher matcher = NUMBERED_ITEM.matcher(answer);
            List<Integer> numbers = new ArrayList<>();
            while (matcher.find()) numbers.add(Integer.parseInt(matcher.group(1)));
            if (numbers.size() != requestedCount) return false;
            for (int i = 0; i < requestedCount; i++) if (numbers.get(i) != i + 1) return false;
        }
        return true;
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
