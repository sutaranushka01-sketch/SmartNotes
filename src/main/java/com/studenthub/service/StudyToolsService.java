package com.studenthub.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Study tools built with plain Java (no AI):
 *  1. Extractive summary  - picks the most important sentences of the notes
 *  2. Key terms + glossary - from "X is ..." / "X means ..." sentences
 *  3. Flashcards           - real questions on the front, a detailed explanation on the back
 *
 * Tasks / exercise lines, examples and sentences about people (names) are ignored everywhere,
 * because they are instructions or stories, not facts.
 */
@Service
public class StudyToolsService {

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "the", "and", "but", "for", "with", "about", "into", "from", "when", "where", "why", "how",
            "all", "any", "both", "each", "few", "more", "most", "other", "some", "such", "nor", "not",
            "only", "own", "same", "than", "too", "very", "can", "will", "just", "should", "now",
            "are", "was", "were", "been", "being", "have", "has", "had", "having", "does", "did",
            "this", "that", "these", "those", "its", "they", "them", "their", "which", "who", "whom",
            "what", "also", "used", "uses", "using", "use", "may", "might", "must", "could", "would",
            "called", "known", "many", "much", "often", "like", "between", "through", "during", "before",
            "after", "above", "below", "while", "because", "until", "against", "then", "else", "there",
            "here", "over", "under", "again", "once", "make", "makes", "made", "help", "helps",
            "means", "refers", "allows", "hides", "shows", "consists", "includes", "contains", "provides",
            "take", "takes", "user", "form", "forms", "ability", "wrapping", "reuse", "special", "single", "essential"));

    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z0-9\\-]*");

    private static final Pattern DEFINITION = Pattern.compile(
            "^(?:(?:a|an|the)\\s+)?([A-Za-z][A-Za-z0-9 \\-/]{1,40}?)\\s+"
                    + "(is used to|is used for|refers to|means|consists of|is|are)\\s+(.{15,})$",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> PRONOUNS = new HashSet<>(Arrays.asList(
            "it", "this", "that", "they", "these", "those", "there", "which", "he", "she", "we"));

    // Sentences that START with these are tasks/instructions ("Display departments..."), not facts.
    private static final Set<String> COMMAND_WORDS = new HashSet<>(Arrays.asList(
            "display", "show", "list", "write", "find", "select", "create", "insert", "update", "delete",
            "retrieve", "get", "print", "calculate", "count", "sort", "order", "use", "add", "remove",
            "drop", "alter", "give", "explain", "describe", "define", "compare", "solve", "identify",
            "state", "name", "run", "execute", "fetch", "enter", "type", "draw", "design", "implement",
            "build", "perform", "consider", "note", "see", "refer", "try", "let", "assume", "given"));

    // A real statement contains a verb that asserts something.
    private static final Pattern STATEMENT_VERB = Pattern.compile(
            "\\b(is|are|was|were|be|has|have|had|can|cannot|will|does|do|means|refers|returns|stores|"
                    + "contains|consists|provides|allows|uses|used|helps|ensures|supports|represents|defines|"
                    + "enables|creates|performs|gives|holds|includes|requires|makes|must|should|may|might|"
                    + "occurs|happens|works|runs|handles|checks|prevents|improves|reduces|groups|filters|"
                    + "sorts|joins|combines|selects|removes|adds|updates|deletes)\\b",
            Pattern.CASE_INSENSITIVE);

    // A defined term is a short noun phrase, never a clause like "employees whose salary".
    private static final Pattern CLAUSE_WORDS = Pattern.compile(
            "\\b(whose|which|that|where|when|who|whom|whose|than|if)\\b", Pattern.CASE_INSENSITIVE);

    // Scenario / example / code-like sentences: "Suppose a college database has...", "e.g. ...", "x = 5;"
    private static final Pattern SCENARIO = Pattern.compile(
            "\\b(suppose|assume|assuming|imagine|e\\.g\\.|for example|for instance)\\b|[;=<>]",
            Pattern.CASE_INSENSITIVE);

    // Example data / code identifiers: "Student_ID 999", "emp_id", "ID 101", "roll no 45"
    private static final Pattern EXAMPLE_DATA = Pattern.compile(
            "\\b[A-Za-z]+_[A-Za-z_]+\\b"                          // snake_case identifiers (Student_ID, emp_name)
          + "|\\b(?:id|no|roll|emp|student)\\s*\\d{2,}\\b"          // ID 999, roll no 45
          + "|\\b\\d{3,}\\b",                                     // long standalone numbers (999, 2024)
            Pattern.CASE_INSENSITIVE);

    private static final String BLANK = "_____";
    private static final int MAX_EXTRA_CHARS = 450;

    // ------------------------------------------------------------------ result classes (serialised to JSON)

    public static class GlossaryItem {
        private final String term;
        private final String meaning;

        public GlossaryItem(String term, String meaning) {
            this.term = term;
            this.meaning = meaning;
        }

        public String getTerm() { return term; }
        public String getMeaning() { return meaning; }
    }

    public static class SummaryResult {
        private final List<String> summary;
        private final List<String> keyTerms;
        private final List<GlossaryItem> glossary;

        public SummaryResult(List<String> summary, List<String> keyTerms, List<GlossaryItem> glossary) {
            this.summary = summary;
            this.keyTerms = keyTerms;
            this.glossary = glossary;
        }

        public List<String> getSummary() { return summary; }
        public List<String> getKeyTerms() { return keyTerms; }
        public List<GlossaryItem> getGlossary() { return glossary; }
    }

    public static class Flashcard {
        private final String front;
        private final String back;

        public Flashcard(String front, String back) {
            this.front = front;
            this.back = back;
        }

        public String getFront() { return front; }
        public String getBack() { return back; }
    }

    // ------------------------------------------------------------------ internal model

    private static class Sent {
        int index;
        String text;
        double score;
        String keyTerm;
        String defTerm, defVerb, defRest; // null when the sentence is not a definition

        boolean isDefinition() { return defTerm != null; }
    }

    private static class Analysis {
        final List<Sent> sentences = new ArrayList<>();   // kept in the original order of the notes
        Map<String, Double> keywordScores = new HashMap<>();
        final Set<String> nouns = new HashSet<>();   // nouns found by the POS tagger (empty = no tagger)
        final Set<String> names = new HashSet<>();   // person names found in the notes (never used as terms)
    }

    // ------------------------------------------------------------------ public API

    /** @param maxSentences optional; when null the size depends on the length of the notes. */
    public SummaryResult summarize(String notes, Integer maxSentences) {
        Analysis a = analyze(notes);

        int k = maxSentences != null
                ? maxSentences
                : (int) Math.round(a.sentences.size() * 0.35);
        k = Math.max(2, Math.min(k, 8));
        k = Math.min(k, a.sentences.size());

        // The first sentence usually introduces the topic, so give it a small boost.
        List<Sent> byScore = new ArrayList<>(a.sentences);
        byScore.sort((x, y) -> Double.compare(boosted(y), boosted(x)));
        List<Sent> chosen = new ArrayList<>(byScore.subList(0, k));
        chosen.sort(Comparator.comparingInt(s -> s.index));   // keep the original order of the notes

        List<String> summary = chosen.stream().map(s -> s.text).collect(Collectors.toList());

        List<String> keyTerms = a.keywordScores.entrySet().stream()
                .filter(e -> !a.names.contains(e.getKey()))                        // never show a person's name
                .filter(e -> a.nouns.isEmpty() || a.nouns.contains(e.getKey()))   // key terms are nouns
                .sorted((x, y) -> Double.compare(y.getValue(), x.getValue()))
                .limit(8)
                .map(e -> capitalize(e.getKey()))
                .collect(Collectors.toList());

        List<GlossaryItem> glossary = definitions(a).stream()
                .limit(10)
                .map(s -> new GlossaryItem(capitalize(s.defTerm), meaning(s)))
                .collect(Collectors.toList());

        return new SummaryResult(summary, keyTerms, glossary);
    }

    public List<Flashcard> flashcards(String notes, Integer count) {
        Analysis a = analyze(notes);
        int max = count == null ? 10 : Math.max(1, Math.min(count, 30));

        List<Flashcard> cards = new ArrayList<>();
        Set<String> usedSentences = new HashSet<>();
        Set<String> usedFronts = new HashSet<>();

        // 1) definition cards  ->  "What is X?"  /  detailed explanation
        for (Sent s : definitions(a)) {
            if (cards.size() >= max) break;
            String front = definitionFront(s);//decides how to phrase a question
            if (!usedFronts.add(front.toLowerCase())) continue;
            cards.add(new Flashcard(front, detailedAnswer(a, s, meaning(s))));
            usedSentences.add(s.text);
        }

        // 2) other important facts  ->  "What do your notes say about X?"  /  full explanation
        List<Sent> rest = new ArrayList<>(a.sentences);
        rest.sort((x, y) -> Double.compare(y.score, x.score));
        for (Sent s : rest) {
            if (cards.size() >= max) break;
            if (usedSentences.contains(s.text)) continue;
            String front = "Explain what your notes say about \"" + capitalize(s.keyTerm.toLowerCase()) + "\".";
            if (!usedFronts.add(front.toLowerCase())) {
                // same key term already used: fall back to a fill-the-term question for this sentence
                String blanked = blank(s.text, s.keyTerm);
                if (!blanked.contains(BLANK)) continue;
                front = "Which term completes this statement?  " + blanked;
                if (!usedFronts.add(front.toLowerCase())) continue;
                cards.add(new Flashcard(front,
                        "Answer: " + capitalize(s.keyTerm) + "\n\nFull statement: " + s.text
                                + extraContext(a, s)));
            } else {
                cards.add(new Flashcard(front, detailedAnswer(a, s, s.text)));
            }
            usedSentences.add(s.text);
        }

        if (cards.isEmpty()) {
            throw new IllegalArgumentException(
                    "Could not build flashcards. Add clearer notes with complete sentences.");
        }
        return cards;
    }

    // ------------------------------------------------------------------ analysis

    private Analysis analyze(String notes) {
        if (notes == null || wordCount(notes) < 40) {
            throw new IllegalArgumentException(
                    "Notes are too short (please add at least ~40 words).");
        }
        // Find person names in the WHOLE original text, before examples are removed.
        Set<String> names = NameFilter.findNames(notes);

        notes = ExampleFilter.removeExampleBlocks(notes);
        List<String> sentences = splitSentences(notes);
        sentences.removeIf(ExampleFilter::isExample);
        Analysis a = new Analysis();
        a.names.addAll(names);
        a.keywordScores = scoreKeywords(sentences);
        PosHelper.collectTerms(sentences, this::isValidWord, BAD_TERM_WORDS, a.nouns, new HashSet<>());

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < sentences.size(); i++) {
            String s = sentences.get(i);
            int wc = wordCount(s);
            if (wc < 5 || wc > 45 || !seen.add(s.toLowerCase())) continue;
            if (!isStatement(s)) continue;   // skip tasks, questions and code lines
            if (NameFilter.startsWithName(s, names)) continue;   // skip stories about people: "Amit is a student..."

            double sum = 0, best = -1;
            String bestWord = null;
            for (String w : words(s)) {
                String lw = w.toLowerCase();
                if (!isValidWord(lw)) continue;
                if (names.contains(lw)) continue;   // a name is never the key term
                double sc = a.keywordScores.getOrDefault(lw, 0.0);
                sum += sc;
                if (sc > best) { best = sc; bestWord = w; }
            }
            if (bestWord == null) continue;

            // With the POS tagger the key term is a real noun / noun phrase (e.g. "primary key").
            if (PosHelper.available()) {
                String term = PosHelper.bestTerm(s, a.keywordScores, this::isValidWord, BAD_TERM_WORDS);
                if (term == null) continue;
                bestWord = term;
            }
            if (NameFilter.isName(bestWord, names)) continue;   // key term must not be a person

            Sent sent = new Sent();
            sent.index = i;
            sent.text = s;
            sent.keyTerm = bestWord;
            sent.score = sum / Math.sqrt(wc);

            Matcher m = DEFINITION.matcher(s.replaceAll("[.!?]+$", ""));
            if (m.matches()) {
                String subject = m.group(1).trim();
                String first = subject.split("\\s+")[0].toLowerCase();
                boolean goodTerm = wordCount(subject) <= 5
                        && !PRONOUNS.contains(first)
                        && !COMMAND_WORDS.contains(first)
                        && !NameFilter.isName(subject, names)   // "Amit is ..." is not a definition
                        && !CLAUSE_WORDS.matcher(subject).find()
                        && isGoodTerm(subject)
                        && wordCount(m.group(3).trim()) >= 5;
                if (goodTerm) {
                    sent.defTerm = subject;
                    sent.defVerb = m.group(2).toLowerCase();
                    sent.defRest = m.group(3).trim();
                    sent.keyTerm = subject;
                    sent.score += 3;   // definitions are the most useful sentences
                }
            }
            a.sentences.add(sent);
        }
        if (a.sentences.isEmpty()) {
            throw new IllegalArgumentException(
                    "Could not find usable sentences. Add notes written as complete sentences "
                    + "(definitions and explanations, not just exercise questions).");
        }
        return a;
    }

    private static final Set<String> DANGLING_END = new HashSet<>(Arrays.asList(
            "and", "or", "of", "the", "a", "an", "to", "in", "with", "which", "that", "for", "by",
            "as", "from", "on", "at", "into", "than", "but", "if", "is", "are"));

    /** Rejects cut-off sentences: ends with "&" / "," / "and", broken quotes or brackets, or starts mid-sentence. */
    private boolean isComplete(String s) {
        String t = s.trim();
        if (t.isEmpty()) return false;
        char last = t.charAt(t.length() - 1);
        if ("&,:;-\u2013(/".indexOf(last) >= 0) return false;

        String[] parts = t.replaceAll("[.!?\"\u201D\u2019')]+$", "").split("\\s+");
        if (DANGLING_END.contains(parts[parts.length - 1].toLowerCase())) return false;

        if (Character.isLowerCase(t.charAt(0))) return false;   // starts in the middle of a sentence
        // "Employee" which we have divided ...  -> a noun followed by a relative clause, no main verb
        if (t.matches("(?is)^[\"\u201C]?[A-Za-z0-9 ]{1,30}[\"\u201D]?\\s+(which|whose|where|who)\\b.*")) return false;

        long straight = t.chars().filter(ch -> ch == '"').count();
        long open = t.chars().filter(ch -> ch == '\u201C').count();
        long close = t.chars().filter(ch -> ch == '\u201D').count();
        if (straight % 2 != 0 || open != close) return false;

        long lp = t.chars().filter(ch -> ch == '(').count();
        long rp = t.chars().filter(ch -> ch == ')').count();
        return lp == rp;
    }

    /** True only for sentences that state a fact. Rejects questions, tasks and code lines. */
    private boolean isStatement(String s) {
        String t = s.trim();
        if (t.endsWith("?")) return false;
        if (!isComplete(t)) return false;   // skip cut-off fragments
        if (SCENARIO.matcher(t).find()) return false;   // "Suppose ...", "e.g. ...", code lines
        if (EXAMPLE_DATA.matcher(t).find()) return false;   // skip example rows / values like "Student_ID 999"
        if (!PosHelper.looksLikeStatement(t)) return false;   // needs a noun + a real verb, not a command

        String first = t.split("\\s+")[0].toLowerCase().replaceAll("[^a-z]", "");
        boolean startsWithCommand = COMMAND_WORDS.contains(first);

        String stripped = t.replaceAll("[.!?]+$", "");
        Matcher def = DEFINITION.matcher(stripped);
        if (def.matches()) {
            // "Select statement is used to..." is a real definition (short subject),
            // "Display employees whose salary is..." is a task.
            if (!startsWithCommand) return true;
            return wordCount(def.group(1).trim()) <= 2;
        }

        if (startsWithCommand) return false;
        return STATEMENT_VERB.matcher(t).find();
    }

    // Words that mean a "term" is really a sentence fragment ("Easy because data", "All this information").
    private static final Set<String> BAD_TERM_WORDS = new HashSet<>(Arrays.asList(
            "because", "this", "that", "these", "those", "which", "whose", "where", "when", "while",
            "if", "so", "since", "all", "every", "each", "some", "any", "many", "most", "more", "less",
            "other", "such", "easy", "very", "also", "then", "thus", "hence", "however", "therefore",
            "another", "given", "based", "completed", "entire", "large", "small", "new", "own"));

    /** A defined term must be a clean noun phrase, not a fragment like "Easy because data". */
    private boolean isGoodTerm(String term) {
        List<String> w = words(term).stream().map(String::toLowerCase).collect(Collectors.toList());
        if (w.isEmpty() || w.size() > 4) return false;
        for (String x : w) {
            if (x.length() < 2 || BAD_TERM_WORDS.contains(x)) return false;
        }
        return isValidWord(w.get(w.size() - 1)) && PosHelper.isNounPhrase(term);
    }

    private double boosted(Sent s) {
        return s.index == 0 ? s.score * 1.2 : s.score;
    }

    private List<Sent> definitions(Analysis a) {
        return a.sentences.stream()
                .filter(Sent::isDefinition)
                .sorted((x, y) -> Double.compare(y.score, x.score))
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ flashcard text

    private String definitionFront(Sent s) {
        String term = s.defTerm;
        switch (s.defVerb) {
            case "is used to":
            case "is used for": return "What is " + term + " used for?";
            case "consists of": return "What does " + term + " consist of?";
            case "means":       return "What does " + term + " mean?";
            case "refers to":   return "What does " + term + " refer to?";
            case "are":         return "What are " + term + "?";
            default:            return "What is " + term + "?";
        }
    }

    /**
     * Back of a card: the core answer, then extra explanation taken from the notes
     * (other sentences that mention the same term, or the sentence that follows).
     */
    private String detailedAnswer(Analysis a, Sent s, String core) {
        return "Answer: " + core + extraContext(a, s);
    }

    private String extraContext(Analysis a, Sent s) {
        String termLow = s.keyTerm.toLowerCase();
        List<String> extra = new ArrayList<>();

        // 1) other sentences that talk about the same term
        for (Sent o : a.sentences) {
            if (o == s || o.text.equals(s.text)) continue;
            if (o.text.toLowerCase().contains(termLow)) extra.add(o.text);
            if (extra.size() == 2) break;
        }
        // 2) otherwise the sentences right after it in the notes
        if (extra.isEmpty()) {
            for (Sent o : a.sentences) {
                if (o.index > s.index) extra.add(o.text);
                if (extra.size() == 2) break;
            }
        }
        if (extra.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (String e : extra) {
            if (sb.length() + e.length() > MAX_EXTRA_CHARS && sb.length() > 0) break;
            if (sb.length() > 0) sb.append(' ');
            sb.append(e);
        }
        return "\n\nMore detail from your notes: " + sb;
    }

    /** The answer side of a definition, written as a clean sentence. */
    private String meaning(Sent s) {
        String rest = s.defRest.replaceAll("[.!?\\s]+$", "");
        switch (s.defVerb) {
            case "is used to":  return capitalize(s.defTerm) + " is used to " + rest + ".";
            case "is used for": return capitalize(s.defTerm) + " is used for " + rest + ".";
            case "consists of": return capitalize(s.defTerm) + " consists of " + rest + ".";
            case "means":       return capitalize(s.defTerm) + " means " + rest + ".";
            case "refers to":   return capitalize(s.defTerm) + " refers to " + rest + ".";
            case "are":         return capitalize(s.defTerm) + " are " + rest + ".";
            default:            return capitalize(s.defTerm) + " is " + rest + ".";
        }
    }

    // ------------------------------------------------------------------ text helpers

    private String blank(String sentence, String term) {
        Pattern p = Pattern.compile("\\b((?:a|an|the)\\s+)?" + Pattern.quote(term) + "\\b", Pattern.CASE_INSENSITIVE);
        return p.matcher(sentence).replaceAll(Matcher.quoteReplacement(BLANK));
    }

    private String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private List<String> splitSentences(String notes) {
        List<String> out = new ArrayList<>();
        StringBuilder carry = new StringBuilder();   // lines that continue on the next line
        for (String part : notes.split("(?<=[.!?])\\s+|\\r?\\n+")) {
            String s = part.trim().replaceAll("^[-*\u2022\\d.)\\s]+", "").trim();
            s = s.replaceFirst("^[oO\u00A7]\\s+(?=[A-Z])", "");   // leftover bullet like "O The database"
            if (s.isEmpty()) continue;
            if (carry.length() > 0) {                              // glue the previous unfinished line to this one
                s = carry + " " + s;
                carry.setLength(0);
            }
            if (s.matches("(?is).*(?:[&,]|\\band|\\bor)$")) {      // line ends with "&", "," "and", "or": it continues
                carry.append(s);
                continue;
            }
            out.add(s);
        }
        if (carry.length() > 0) out.add(carry.toString());
        return out;
    }

    private int wordCount(String text) {
        String t = text.trim();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    private List<String> words(String sentence) {
        List<String> out = new ArrayList<>();
        Matcher m = WORD.matcher(sentence);
        while (m.find()) out.add(m.group());
        return out;
    }

    private boolean isValidWord(String lowerWord) {
        return lowerWord.length() >= 3 && !STOP_WORDS.contains(lowerWord);
    }

    /** TF x IDF-style score: frequent words score high, words in every sentence score lower. */
    private Map<String, Double> scoreKeywords(List<String> sentences) {
        Map<String, Integer> tf = new HashMap<>();
        Map<String, Integer> df = new HashMap<>();
        for (String s : sentences) {
            Set<String> seen = new HashSet<>();
            for (String w : words(s)) {
                String lw = w.toLowerCase();
                if (!isValidWord(lw)) continue;
                tf.merge(lw, 1, Integer::sum);
                if (seen.add(lw)) df.merge(lw, 1, Integer::sum);
            }
        }
        int n = sentences.size();
        Map<String, Double> scores = new HashMap<>();
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            double idf = Math.log((n + 1.0) / (df.get(e.getKey()) + 0.5)) + 1;
            scores.put(e.getKey(), e.getValue() * idf);
        }
        return scores;
    }
}