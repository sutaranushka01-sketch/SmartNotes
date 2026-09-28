package com.studenthub.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api/quiz")
@CrossOrigin(origins = "*")
public class QuizGenerationController {

    @Value("${gemini.api.key}")
    private String apiKey;

    // Swap for whichever Gemini model your key has access to, e.g. "gemini-2.5-flash" or "gemini-2.5-pro"
    private static final String MODEL = "gemini-3.8-flash";
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public static class GenerateRequest {
        public String notes;
        public int count = 6;
        public String difficulty = "medium"; // easy | medium | hard
        // any of: "mcq", "tf", "short". Empty/null => multiple choice only.
        public List<String> types;
    }

    @PostMapping("/generate")
    public List<Map<String, Object>> generate(@RequestBody GenerateRequest req) throws Exception {
        if (req.notes == null || req.notes.trim().length() < 30) {
            throw new RuntimeException("Notes are too short to generate a quiz.");
        }

        String difficultyGuidance = switch (req.difficulty) {
            case "easy" -> "Test recall and basic definitions of the concepts.";
            case "hard" -> "Test deeper understanding: application, comparison between concepts, cause-and-effect, edge cases, and 'why' rather than 'what'. Include at least one scenario-based question where the student must apply the concept.";
            default -> "Test conceptual understanding, not just definitions — include some 'why' or 'how' questions, not only 'what is X'.";
        };

        // ---- decide how many questions of each type ----
        List<String> types = new ArrayList<>();
        if (req.types != null) {
            for (String t : req.types) {
                if (List.of("mcq", "tf", "short").contains(t) && !types.contains(t)) types.add(t);
            }
        }
        if (types.isEmpty()) types.add("mcq");

        int total = Math.max(1, Math.min(req.count, 20));
        Map<String, Integer> perType = new LinkedHashMap<>();
        for (int i = 0; i < types.size(); i++) {
            // split evenly; the first types get the remainder
            perType.put(types.get(i), total / types.size() + (i < total % types.size() ? 1 : 0));
        }
        StringBuilder mix = new StringBuilder();
        perType.forEach((t, n) -> {
            if (n > 0) mix.append("- ").append(n).append(" x \"").append(t).append("\"\n");
        });

        String systemPrompt = """
            You write quiz questions for B.Tech (undergraduate engineering) students,
            based only on the notes they provide. Assume the reader has the technical
            maturity of a university engineering student, not a school student.

            There are three question types. Every question object has a "type" field.

            1. "mcq"   - multiple choice. Exactly 4 options, "correctIndex" is 0-3.
               All four options must be plausible to someone who only partly understood the
               material; no silly distractors.
            2. "tf"    - true/false. "question" is ONE clear statement (not a question) that is
               definitely true or definitely false according to the notes. "options" is
               exactly ["True", "False"]; "correctIndex" is 0 for True, 1 for False.
               Make roughly half of the tf statements false, using a subtle but clear
               change to a fact in the notes (not a trick of wording).
            3. "short" - short answer. "question" can be answered with a single term or short
               phrase of at most 6 words. Put that expected answer in "answer". Do NOT include
               "options" or "correctIndex".

            Rules:
            - Base every question strictly on the notes. Do not invent facts not implied by them.
            - Test understanding of the concept, not word-matching.
            - Vary phrasing: "Which of the following...", "What happens when...", "Why does...", scenarios, comparisons.
            - Respond with ONLY a JSON array, nothing else. Shapes:
            [
              {"type":"mcq","topic":"2-4 words","question":"...","options":["A","B","C","D"],"correctIndex":0},
              {"type":"tf","topic":"2-4 words","question":"statement","options":["True","False"],"correctIndex":1},
              {"type":"short","topic":"2-4 words","question":"...","answer":"expected answer"}
            ]
            """;

        String userPrompt = "Notes:\n" + req.notes
                + "\n\nGenerate exactly " + total + " questions, with this mix of types:\n" + mix
                + difficultyGuidance;

        Map<String, Object> body = new HashMap<>();
        body.put("system_instruction", Map.of(
                "parts", List.of(Map.of("text", systemPrompt))
        ));
        body.put("contents", List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))
        ));
        // Ask Gemini to return raw JSON instead of markdown-wrapped text
        body.put("generationConfig", Map.of(
                "responseMimeType", "application/json",
                "temperature", 0.7
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = ENDPOINT + "?key=" + apiKey;

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, entity, String.class);
        } catch (org.springframework.web.client.HttpServerErrorException ex) {
            // Gemini is transiently overloaded (503) — wait briefly and retry once
            if (ex.getStatusCode().value() == 503) {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                response = restTemplate.postForEntity(url, entity, String.class);
            } else {
                throw ex;
            }
        }

        JsonNode root = mapper.readTree(response.getBody());
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new RuntimeException("Gemini returned no candidates: " + response.getBody());
        }
        String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText();

        // Defensive cleanup in case the model wraps the JSON in a code fence anyway
        text = text.trim();
        if (text.startsWith("```")) {
            text = text.replaceAll("^```[a-zA-Z]*\\n", "").replaceAll("```$", "").trim();
        }

        JsonNode questionsNode = mapper.readTree(text);
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode q : questionsNode) {
            String type = q.path("type").asText("mcq");
            String questionText = q.path("question").asText("").trim();
            if (questionText.isEmpty()) continue;

            Map<String, Object> question = new HashMap<>();
            question.put("topic", q.path("topic").asText("Notes"));
            question.put("question", questionText);

            if ("short".equals(type)) {
                String answer = q.path("answer").asText("").trim();
                if (answer.isEmpty()) continue; // unusable without an expected answer
                question.put("type", "short");
                question.put("options", new ArrayList<String>());
                question.put("answer", answer);
                question.put("correctIndex", -1);
            } else if ("tf".equals(type)) {
                int idx = q.path("correctIndex").asInt(0);
                if (idx != 0 && idx != 1) continue;
                question.put("type", "tf");
                question.put("options", List.of("True", "False"));
                question.put("correctIndex", idx);
            } else {
                List<String> options = new ArrayList<>();
                q.path("options").forEach(o -> options.add(o.asText()));
                int idx = q.path("correctIndex").asInt(0);
                if (options.size() != 4 || idx < 0 || idx > 3) continue;
                question.put("type", "mcq");
                question.put("options", options);
                question.put("correctIndex", idx);
            }
            result.add(question);
        }
        if (result.isEmpty()) {
            throw new RuntimeException("Gemini returned no usable questions: " + text);
        }
        return result;
    }
}