package com.studenthub.service;

import com.studenthub.dto.QuizQuestion;
import com.studenthub.dto.QuizQuestion.Type;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rule-based quiz generator (no AI). MCQ ONLY.
 *
 * Pipeline: split sentences -> keep only real facts -> score keywords (TF-IDF style)
 *   -> pick the blanked term by DIFFICULTY (generic words like "data" are never blanked)
 *   -> order sentences by difficulty -> build MCQs:
 *        EASY   : definitions + obvious blanks, clearly different options
 *        MEDIUM : less obvious blanks, related options, some "which statement is correct"
 *        HARD   : specific blanks, look-alike options, many "which statement is correct"
 *   -> distractors from the SAME slot / defined terms, with original casing (DBMS stays DBMS)
 *   -> quality filter.
 */
@Service
public class QuizGeneratorService {

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

    /**
     * Words so generic that blanking them tests nothing ("_____ is stored in a systematic way" -> "data").
     * They are never chosen as the blanked answer (a defined term like "Data" is still allowed on EASY).
     */
    private static final Set<String> GENERIC_TERMS = new HashSet<>(Arrays.asList(
            "data", "information", "system", "systems", "database", "databases", "software", "computer",
            "computers", "application", "applications", "program", "programs", "method", "methods", "way",
            "ways", "result", "results", "item", "items", "value", "values", "process", "number", "numbers",
            "thing", "things", "example", "examples", "part", "parts", "type", "types", "kind", "set", "sets",
            "name", "names", "level", "levels", "time", "place", "work", "step", "steps", "case", "cases"));

    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z0-9\\-]*");

    // "X is ...", "X refers to ...", "X consists of ..."
    private static final Pattern DEFINITION = Pattern.compile(
            "^(?:(?:a|an|the)\\s+)?([A-Za-z][A-Za-z0-9 \\-/]{1,40}?)\\s+"
                    + "(is used to|is used for|refers to|means|consists of|is|are)\\s+(.{15,})$",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> PRONOUNS = new HashSet<>(Arrays.asList(
            "it", "this", "that", "they", "these", "those", "there", "which", "he", "she", "we"));

    // Words that mean a "term" is really a sentence fragment ("Easy because data", "All this information").
    private static final Set<String> BAD_TERM_WORDS = new HashSet<>(Arrays.asList(
            "because", "this", "that", "these", "those", "which", "whose", "where", "when", "while",
            "if", "so", "since", "all", "every", "each", "some", "any", "many", "most", "more", "less",
            "other", "such", "easy", "very", "also", "then", "thus", "hence", "however", "therefore",
            "another", "given", "based", "completed", "entire", "large", "small", "new", "own"));

    // Terms that may never be the blanked answer: fragments + generic words.
    private static final Set<String> BAD_ANSWER_WORDS = new HashSet<>();
    static {
        BAD_ANSWER_WORDS.addAll(BAD_TERM_WORDS);
        BAD_ANSWER_WORDS.addAll(GENERIC_TERMS);
    }

    // Sentences that START with these are tasks/instructions ("Display departments..."), not facts.
    private static final Set<String> COMMAND_WORDS = new HashSet<>(Arrays.asList(
            "display", "show", "list", "write", "find", "select", "create", "insert", "update", "delete",
            "retrieve", "get", "print", "calculate", "count", "sort", "order", "use", "add", "remove",
            "drop", "alter", "give", "explain", "describe", "define", "compare", "solve", "identify",
            "state", "name", "run", "execute", "fetch", "enter", "type", "draw", "design", "implement",
            "build", "perform", "consider", "note", "see", "refer", "try", "let", "assume", "given",
            "suppose", "imagine", "example", "if"));

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

    // A real statement contains a verb that asserts something.
    private static final Pattern STATEMENT_VERB = Pattern.compile(
            "\\b(is|are|was|were|be|has|have|had|can|cannot|will|does|do|means|refers|returns|stores|"
                    + "contains|consists|provides|allows|uses|used|helps|ensures|supports|represents|defines|"
                    + "enables|creates|performs|gives|holds|includes|requires|makes|must|should|may|might|"
                    + "occurs|happens|works|runs|handles|checks|prevents|improves|reduces|groups|filters|"
                    + "sorts|joins|combines|selects|removes|adds|updates|deletes)\\b",
            Pattern.CASE_INSENSITIVE);

    // All-caps words that are never a "topic" of the notes.
    private static final Set<String> NOT_TOPICS = new HashSet<>(Arrays.asList(
            "AND", "OR", "NOT", "NULL", "ID", "NO", "THE", "IS", "AS", "IN", "ON", "BY", "OF", "TO"));

    private static final String BLANK = "_____";

    private final Random random = new Random();

    /** Words usable as wrong options; "preferred" = defined terms (best distractors). */
    private static class Pool {
        final List<String> words = new ArrayList<>();
        final Set<String> preferred = new HashSet<>();
        final List<String> sentences = new ArrayList<>();   // every sentence of the notes
        final Set<String> nouns = new HashSet<>();           // nouns found by the POS tagger (empty = no tagger)
        final Set<String> phrases = new HashSet<>();         // noun phrases such as "primary key"
        final Set<String> names = new HashSet<>();           // person names found in the notes (never used)
        final Map<String, String> canon = new HashMap<>();   // lower-case -> original casing for acronyms (dbms -> DBMS)
        String topic;                                        // most frequent acronym in the notes (e.g. "DBMS") or null
    }

    private static class Candidate {
        String sentence;
        double score;
        String keyTerm;
        String defTerm, defVerb, defRest; // null if not a definition sentence

        boolean isDefinition() { return defTerm != null; }
    }

    /** A built question plus the sentence it came from (used to avoid repeating sentences). */
    private static class Built {
        final QuizQuestion question;
        final String sentence;

        Built(QuizQuestion question, String sentence) {
            this.question = question;
            this.sentence = sentence;
        }
    }

    // ------------------------------------------------------------------ public API

    /** Old signature kept so existing callers still compile. */
    public List<QuizQuestion> generate(String notes, int count, String difficulty) {
        return generate(notes, count, difficulty, null);
    }

    /**
     * @param requestedTypes ignored: every quiz is MCQ only.
     *                       Kept only so older callers still compile.
     */
    public List<QuizQuestion> generate(String notes, int count, String difficulty, List<String> requestedTypes) {
        if (notes == null || wordCount(notes) < 40) {
            throw new IllegalArgumentException(
                    "Notes are too short to build a good quiz (please add at least ~40 words).");
        }
        count = Math.max(1, Math.min(count, 30));
        String level = difficulty == null ? "MEDIUM" : difficulty.trim().toUpperCase();
        if (!level.equals("EASY") && !level.equals("HARD")) level = "MEDIUM";

        // First try with the strict quality rules; only relax them if nothing at all could be built.
        List<QuizQuestion> result = run(notes, count, level, true);
        if (result.isEmpty()) result = run(notes, count, level, false);

        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "Could not build a quiz from these notes. "
                    + "Add notes written as complete sentences and definitions "
                    + "(for example: \"A primary key uniquely identifies each row in a table.\").");
        }
        return result;
    }

    private List<QuizQuestion> run(String notes, int count, String level, boolean strict) {
        // Find person names in the WHOLE original text, before examples are removed.
        final Set<String> names = NameFilter.findNames(notes);

        notes = ExampleFilter.removeExampleBlocks(notes);
        List<String> sentences = splitSentences(notes);
        sentences.removeIf(ExampleFilter::isExample);
        Map<String, Double> scores = scoreKeywords(sentences);

        Pool pool = new Pool();
        pool.sentences.addAll(sentences);
        pool.names.addAll(names);
        buildCasing(sentences, pool);

        // With the POS tagger, wrong options are limited to real nouns / noun phrases.
        boolean pos = PosHelper.available();//part of speech
        if (pos) PosHelper.collectTerms(sentences, this::isValidWord, BAD_TERM_WORDS, pool.nouns, pool.phrases);

        // A wrong option must be a term that really recurs in the notes (a one-off word like a quoted
        // example is a poor option). The relaxed second run accepts words that appear only once.
        final int minCount = strict ? 2 : 1;
        Map<String, Integer> freq = new HashMap<>();
        for (String s : sentences) for (String w : lowerWords(s)) freq.merge(w, 1, Integer::sum);

        List<String> topWords = scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(80).map(Map.Entry::getKey)
                .filter(w -> !names.contains(w))                    // a person's name is never an option
                .filter(w -> freq.getOrDefault(w, 0) >= minCount)
                .collect(Collectors.toList());
        if (pos) topWords = topWords.stream().filter(pool.nouns::contains).collect(Collectors.toList());
        pool.words.addAll(topWords);
        if (pos) {
            for (String phrase : pool.phrases) {
                if (!NameFilter.isName(phrase, names)
                        && countOccurrences(sentences, phrase) >= minCount) pool.words.add(phrase);
            }
        }

        List<Candidate> ranked = rankSentences(sentences, scores, names, level);//ranks sentences
        for (Candidate c : ranked) {              // definition terms make the best distractors
            if (c.isDefinition()) {
                pool.words.add(c.defTerm);
                pool.preferred.add(c.defTerm.toLowerCase());
            }
        }

        // Every quiz is MCQ only.
        List<Type> schedule = new ArrayList<>(Collections.singletonList(Type.MCQ));

        // Difficulty decides WHICH sentences are used first (simple -> complex).
        ranked = orderByDifficulty(ranked, level);

        // 1) Build every question we can.
        Map<Type, List<Built>> buckets = new EnumMap<>(Type.class);
        for (Type t : schedule) buckets.put(t, new ArrayList<>());

        for (int r = 0; r < ranked.size(); r++) {
            Candidate c = ranked.get(r);
            for (Type t : schedule) {
                QuizQuestion q = build(t, c, r, pool, level, strict);
                if (q != null && isGood(q)) {
                    buckets.get(t).add(new Built(q, c.sentence));
                }
            }
        }

        // 2) Pick questions. Pass 0 never reuses a sentence; pass 1 fills any gaps.
        //    Identical question text is never added twice.
        List<QuizQuestion> result = new ArrayList<>();
        Set<Built> chosen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> usedSentences = new HashSet<>();
        Set<String> usedQuestions = new HashSet<>();

        for (int pass = 0; pass < 2 && result.size() < count; pass++) {
            boolean progress = true;
            while (result.size() < count && progress) {
                progress = false;
                for (Type t : schedule) {
                    if (result.size() >= count) break;
                    for (Built b : buckets.get(t)) {
                        if (chosen.contains(b)) continue;
                        if (pass == 0 && usedSentences.contains(b.sentence)) continue;
                        if (!usedQuestions.add(b.question.getQuestion())) continue;
                        chosen.add(b);
                        usedSentences.add(b.sentence);
                        result.add(b.question);
                        progress = true;
                        break;
                    }
                }
            }
        }

        Collections.shuffle(result, random);
        return result;
    }

    private int countOccurrences(List<String> sentences, String phrase) {
        int count = 0;
        for (String s : sentences) {
            String low = s.toLowerCase();
            for (int at = low.indexOf(phrase); at >= 0; at = low.indexOf(phrase, at + phrase.length())) count++;
        }
        return count;
    }

    // ------------------------------------------------------------------ casing + topic

    /**
     * Remembers how acronyms are written in the notes (dbms -> DBMS) so options never turn into "Dbms",
     * and finds the notes' main acronym (used as context: "In DBMS, ...").
     */
    private void buildCasing(List<String> sentences, Pool pool) {
        Set<String> plain = new HashSet<>();            // words seen in normal casing (lower / Capitalised)
        Map<String, String> upperForm = new HashMap<>();
        Map<String, Integer> acronymCount = new HashMap<>();
        for (String s : sentences) {
            for (String w : words(s)) {
                String lw = w.toLowerCase();
                if (upperCount(w) >= 2) {
                    upperForm.putIfAbsent(lw, w);
                    if (isAllCapsAcronym(w)) acronymCount.merge(w, 1, Integer::sum);
                } else {
                    plain.add(lw);
                }
            }
        }
        for (Map.Entry<String, String> e : upperForm.entrySet()) {
            // "WHAT IS A DBMS" would wrongly make "what" an acronym, so skip words also seen in plain casing.
            if (!plain.contains(e.getKey())) pool.canon.put(e.getKey(), e.getValue());
        }
        int best = 2;   // an acronym must occur at least 3 times to count as the topic
        for (Map.Entry<String, Integer> e : acronymCount.entrySet()) {
            if (NOT_TOPICS.contains(e.getKey()) || !pool.canon.containsKey(e.getKey().toLowerCase())) continue;
            if (e.getValue() > best) { best = e.getValue(); pool.topic = e.getKey(); }
        }
    }

    private int upperCount(String w) {
        int n = 0;
        for (int i = 0; i < w.length(); i++) if (Character.isUpperCase(w.charAt(i))) n++;
        return n;
    }

    private boolean isAllCapsAcronym(String w) {
        if (w.length() < 2 || w.length() > 6) return false;
        for (int i = 0; i < w.length(); i++) if (!Character.isUpperCase(w.charAt(i))) return false;
        return true;
    }

    /** Applies the notes' own casing to acronyms inside a term ("dbms schema" -> "DBMS schema"). */
    private String canonical(String term, Pool pool) {
        String[] tokens = term.split(" ");
        for (int i = 0; i < tokens.length; i++) {
            String fixed = pool.canon.get(tokens[i].toLowerCase());
            if (fixed != null) tokens[i] = fixed;
        }
        return String.join(" ", tokens);
    }

    /** "In DBMS, " context prefix, or "" when there is no topic / it would leak or repeat the answer. */
    private String topicPrefix(Pool pool, String sentence, String answer) {
        if (pool.topic == null) return "";
        if (sentence.toLowerCase().contains(pool.topic.toLowerCase())) return "";
        if (answer != null && answer.toLowerCase().contains(pool.topic.toLowerCase())) return "";
        return "In " + pool.topic + ", ";
    }

    private String upperFirst(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ difficulty

    /** Rough complexity: long sentences and long technical words are harder. */
    private int complexity(Candidate c) {
        int longWords = 0;
        for (String w : words(c.sentence)) if (w.length() >= 9) longWords++;
        return wordCount(c.sentence) + 2 * longWords;
    }

    /** Easy = simplest sentences first, Hard = most complex first, Medium = closest to the middle. */
    private List<Candidate> orderByDifficulty(List<Candidate> ranked, String level) {
        List<Candidate> list = new ArrayList<>(ranked);
        Map<Candidate, Double> key = new IdentityHashMap<>();
        for (Candidate c : list) key.put(c, complexity(c) + random.nextDouble() * 3);   // small noise so "Retry" varies
        List<Double> vals = new ArrayList<>(key.values());
        Collections.sort(vals);
        final double median = vals.isEmpty() ? 0 : vals.get(vals.size() / 2);

        Comparator<Candidate> cmp;
        switch (level) {
            case "EASY": cmp = (x, y) -> Double.compare(key.get(x), key.get(y)); break;
            case "HARD": cmp = (x, y) -> Double.compare(key.get(y), key.get(x)); break;
            default:     cmp = (x, y) -> Double.compare(Math.abs(key.get(x) - median), Math.abs(key.get(y) - median));
        }
        list.sort(cmp);
        return list;
    }

    // ------------------------------------------------------------------ text analysis

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

    private List<String> lowerWords(String sentence) {
        return words(sentence).stream().map(String::toLowerCase).collect(Collectors.toList());
    }

    private boolean isValidWord(String lowerWord) {
        return lowerWord.length() >= 3 && !STOP_WORDS.contains(lowerWord);
    }

    /** A word that may be the blanked answer: valid and not generic ("data", "system", ...). */
    private boolean isAnswerWord(String lowerWord) {
        return isValidWord(lowerWord) && !GENERIC_TERMS.contains(lowerWord);
    }

    /** True for a single generic word ("data"). Multi-word terms such as "database schema" are fine. */
    private boolean isGenericTerm(String term) {
        return wordCount(term) == 1 && GENERIC_TERMS.contains(term.trim().toLowerCase());
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

    /** True only for sentences that state a fact. Rejects questions, tasks, scenarios and code lines. */
    private boolean isStatement(String s) {
        String t = s.trim();
        if (t.endsWith("?")) return false;
        if (!isComplete(t)) return false;   // skip cut-off fragments
        if (SCENARIO.matcher(t).find()) return false;
        if (EXAMPLE_DATA.matcher(t).find()) return false;   // skip example rows / values like "Student_ID 999"
        if (!PosHelper.looksLikeStatement(t)) return false;   // needs a noun + a real verb, not a command

        String first = t.split("\\s+")[0].toLowerCase().replaceAll("[^a-z]", "");
        boolean startsWithCommand = COMMAND_WORDS.contains(first);

        String stripped = t.replaceAll("[.!?]+$", "");
        Matcher def = DEFINITION.matcher(stripped);
        if (def.matches()) {
            // "Select statement is used to..." is a real definition (short subject),
            // "Display departments whose minimum salary is..." is a task.
            if (!startsWithCommand) return true;
            return wordCount(def.group(1).trim()) <= 2;
        }

        if (startsWithCommand) return false;
        return STATEMENT_VERB.matcher(t).find();
    }

    /**
     * Index of the blank among the sentence's terms ranked by score.
     * EASY = the most central term, MEDIUM = the next one, HARD = a more specific one.
     */
    private int pickRank(String level, int size) {
        if (size <= 1) return 0;
        if (level.equals("HARD")) return Math.min(2, size - 1);
        if (level.equals("MEDIUM")) return Math.min(1, size - 1);
        return 0;
    }

    private List<Candidate> rankSentences(List<String> sentences, Map<String, Double> scores,
                                          Set<String> names, String level) {
        List<Candidate> list = new ArrayList<>();
        Set<String> seenSentences = new HashSet<>();
        final boolean pos = PosHelper.available();
        for (String s : sentences) {
            int wc = wordCount(s);
            if (wc < 6 || wc > 35 || !seenSentences.add(s.toLowerCase())) continue;
            if (!isStatement(s)) continue;   // skip tasks, scenarios, questions and code lines
            if (NameFilter.startsWithName(s, names)) continue;   // skip stories about people: "Amit is a student..."

            // "It stores data..." / "This is used..." have no context on their own.
            String firstWord = s.trim().split("\\s+")[0].toLowerCase().replaceAll("[^a-z]", "");
            if (PRONOUNS.contains(firstWord)) continue;

            double sum = 0;
            List<String> termWords = new ArrayList<>();   // answer-worthy words of this sentence
            Set<String> seenTerms = new HashSet<>();
            for (String w : words(s)) {
                String lw = w.toLowerCase();
                if (!isValidWord(lw)) continue;
                if (names.contains(lw)) continue;   // a name is never the blanked term
                sum += scores.getOrDefault(lw, 0.0);
                if (isAnswerWord(lw) && seenTerms.add(lw)) termWords.add(w);
            }
            if (termWords.isEmpty()) continue;
            termWords.sort((a, b) -> Double.compare(scores.getOrDefault(b.toLowerCase(), 0.0),
                    scores.getOrDefault(a.toLowerCase(), 0.0)));

            int rank = pickRank(level, termWords.size());
            String bestWord = termWords.get(rank);

            // With the POS tagger the blank is a real noun / noun phrase (e.g. "primary key").
            if (pos) {
                Map<String, Double> sc = scores;
                if (rank > 0) {   // skip the most obvious term(s) of this sentence for harder levels
                    sc = new HashMap<>(scores);
                    for (int i = 0; i < rank; i++) sc.remove(termWords.get(i).toLowerCase());
                }
                String term = PosHelper.bestTerm(s, sc, w -> isAnswerWord(w), BAD_ANSWER_WORDS);
                if (term == null && sc != scores) {
                    term = PosHelper.bestTerm(s, scores, w -> isAnswerWord(w), BAD_ANSWER_WORDS);
                }
                if (term == null) continue;
                bestWord = term;
            }
            if (NameFilter.isName(bestWord, names)) continue;   // the blanked term must not be a person
            if (isGenericTerm(bestWord)) continue;              // never blank "data", "system", ...

            Candidate c = new Candidate();
            c.sentence = s;
            c.keyTerm = bestWord;
            c.score = sum / Math.sqrt(wc);

            Matcher m = DEFINITION.matcher(s.replaceAll("[.!?]+$", ""));
            if (m.matches()) {
                String subject = m.group(1).trim();
                String first = subject.split("\\s+")[0].toLowerCase();
                // A definition of a generic word ("Data is ...") is fine for EASY, too trivial above that.
                boolean genericOk = level.equals("EASY") || !isGenericTerm(subject);
                if (wordCount(subject) <= 5 && !PRONOUNS.contains(first)
                        && !NameFilter.isName(subject, names)   // "Amit is ..." is not a definition
                        && genericOk
                        && isGoodTerm(subject)
                        && wordCount(m.group(3).trim()) >= 5) {
                    c.defTerm = subject;
                    c.defVerb = m.group(2).toLowerCase();
                    c.defRest = m.group(3).trim();
                    c.keyTerm = subject;   // blank the defined term itself
                    c.score += 3;          // definitions make the best questions
                }
            }
            list.add(c);
        }
        list.sort((a, b) -> Double.compare(b.score, a.score));
        return list;
    }

    /** A defined term must be a clean noun phrase, not a fragment like "Easy because data". */
    private boolean isGoodTerm(String term) {
        List<String> w = lowerWords(term);
        if (w.isEmpty() || w.size() > 4) return false;
        for (String x : w) {
            if (x.length() < 2 || BAD_TERM_WORDS.contains(x)) return false;
        }
        return isValidWord(w.get(w.size() - 1)) && PosHelper.isNounPhrase(term);
    }

    // ------------------------------------------------------------------ question builders

    /** idx = position of the sentence after difficulty ordering; used to mix question styles. */
    private QuizQuestion build(Type t, Candidate c, int idx, Pool pool, String level, boolean strict) {
        switch (t) {
            case FILL_BLANK:
                return buildFillBlank(c, level);
            case MCQ: {
                // Medium/Hard: some "which statement is correct" questions (close options, harder to guess).
                if (!level.equals("EASY")) {
                    boolean statement = level.equals("HARD") ? idx % 2 == 0 : idx % 3 == 1;
                    if (statement) {
                        QuizQuestion sq = buildStatementMcq(c, pool, level);
                        if (sq != null) return sq;
                    }
                }
                // Definition questions are the best ones, so they come first.
                if (c.isDefinition()) {
                    QuizQuestion q = buildDefinitionMcq(c, pool, level);
                    return q != null ? q : buildMcq(c, pool, level, strict);
                }
                return buildMcq(c, pool, level, strict);
            }
            case TRUE_FALSE:
                return buildTrueFalse(c, idx, pool, level);
            case SHORT_ANSWER:
                return buildShortAnswer(c, level);
            default:
                return null;
        }
    }

    private Pattern termPattern(String term) {
        return Pattern.compile("\\b((?:a|an|the)\\s+)?" + Pattern.quote(term) + "\\b", Pattern.CASE_INSENSITIVE);
    }

    /** Replaces the term (and a leading a/an/the, so the article does not give the answer away) with a blank. */
    private String blank(String sentence, String term) {
        return termPattern(term).matcher(sentence).replaceAll(Matcher.quoteReplacement(BLANK));
    }

    /** Swaps one term for another and fixes the article (a/an) and capital letter. */
    private String swapTerm(String sentence, String term, String replacement) {
        Matcher m = termPattern(term).matcher(sentence);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String article = m.group(1);
            String out = replacement;
            if (article != null) {
                String art = article.trim().toLowerCase();
                if (!art.equals("the")) {
                    art = "aeiou".indexOf(Character.toLowerCase(replacement.charAt(0))) >= 0 ? "an" : "a";
                }
                if (Character.isUpperCase(article.charAt(0))) art = Character.toUpperCase(art.charAt(0)) + art.substring(1);
                out = art + " " + replacement;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(out));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private QuizQuestion buildFillBlank(Candidate c, String level) {
        String blanked = blank(c.sentence, c.keyTerm);
        if (!blanked.contains(BLANK)) return null;
        return new QuizQuestion(Type.FILL_BLANK, "Fill in the blank: " + blanked,
                null, c.keyTerm, level, c.sentence);
    }

    /** Fill-in-the-blank MCQ. Skipped when the blank has too little context or no sensible wrong options. */
    private QuizQuestion buildMcq(Candidate c, Pool pool, String level, boolean strict) {
        String blanked = blank(c.sentence, c.keyTerm);
        if (!blanked.contains(BLANK) || blanked.indexOf(BLANK) != blanked.lastIndexOf(BLANK)) return null;

        // Enough context around the blank, and a real term (not a tiny word).
        boolean easy = level.equals("EASY");
        if (wordCount(blanked) < (easy ? 7 : 8) || c.keyTerm.length() < 3) return null;

        // Above Easy, a short fragment whose blank is the opening subject ("_____ is stored safely") is too guessable.
        if (!easy && blanked.startsWith(BLANK) && wordCount(blanked) < 10) return null;

        // Strict mode: wrong options must exist in the SAME slot elsewhere in the notes.
        // (With the POS tagger the options are already nouns, so this check is only needed without it.)
        if (strict && !PosHelper.available() && contextTerms(c.keyTerm, c.sentence, pool).isEmpty()) return null;

        List<String> d = distractors(c.keyTerm, c.sentence, pool, 3, level, true);
        if (d.size() < 3) return null;
        List<String> options = new ArrayList<>(d);
        options.add(c.keyTerm);
        Collections.shuffle(options, random);

        // The correct answer MUST be one of the displayed options.
        if (options.stream().noneMatch(o -> o.equalsIgnoreCase(c.keyTerm))) return null;

        String stem = topicPrefix(pool, c.sentence, c.keyTerm)
                + (easy ? "choose the option that best completes the sentence: "
                        : "which term best completes the sentence: ");
        stem = upperFirst(stem);
        return new QuizQuestion(Type.MCQ, stem + blanked, options, c.keyTerm, level, c.sentence);
    }

    private QuizQuestion buildDefinitionMcq(Candidate c, Pool pool, String level) {
        List<String> d = distractors(c.defTerm, c.sentence, pool, 3, level, true);
        if (d.size() < 3) return null;
        List<String> options = new ArrayList<>(d);
        options.add(c.defTerm);
        Collections.shuffle(options, random);

        // The correct answer MUST be one of the displayed options.
        if (options.stream().noneMatch(o -> o.equalsIgnoreCase(c.defTerm))) return null;

        String q;
        if (level.equals("EASY")) {
            q = "Which term completes this definition? " + BLANK + " " + c.defVerb + " " + c.defRest;
        } else {
            // Without "X is ..." the grammar no longer hints at the answer.
            String rest = upperFirst(c.defRest);
            if (!rest.endsWith(".")) rest = rest + ".";
            q = "Which term is described by the following? " + rest;
        }
        return new QuizQuestion(Type.MCQ, q, options, c.defTerm, level, c.sentence);
    }

    /**
     * "Which statement is correct?" - the true sentence next to three copies where one key term is swapped.
     * Options differ by a single term, so the student must really know the fact.
     */
    private QuizQuestion buildStatementMcq(Candidate c, Pool pool, String level) {
        // Swapping a term only gives grammatical sentences when options are real nouns (needs the POS tagger).
        if (!PosHelper.available()) return null;
        String correct = c.sentence.trim();
        if (c.keyTerm == null || wordCount(correct) < 8 || wordCount(correct) > 30) return null;
        if (!termPattern(c.keyTerm).matcher(correct).find()) return null;

        // Same-slot words could also be true there, so only use unrelated terms for swapped statements.
        List<String> d = distractors(c.keyTerm, c.sentence, pool, 3, level, false);
        if (d.size() < 3) return null;

        LinkedHashSet<String> opts = new LinkedHashSet<>();
        opts.add(correct);
        for (String x : d) {
            String wrong = swapTerm(correct, c.keyTerm, x);
            if (!wrong.equals(correct)) opts.add(wrong);
        }
        if (opts.size() < 4) return null;

        List<String> options = new ArrayList<>(opts);
        Collections.shuffle(options, random);

        String prefix = topicPrefix(pool, c.sentence, c.keyTerm);
        String q = prefix.isEmpty() ? "Which of the following statements is correct?"
                : prefix + "which of the following statements is correct?";
        return new QuizQuestion(Type.MCQ, q, options, correct, level, c.sentence);
    }

    /** Not used while the quiz is MCQ-only; kept so the Type enum still compiles everywhere. */
    private QuizQuestion buildTrueFalse(Candidate c, int idx, Pool pool, String level) {
        boolean makeFalse = level.equals("EASY") ? idx % 3 == 2
                : level.equals("HARD") ? idx % 3 != 0
                : idx % 2 == 1;
        if (makeFalse) {
            List<String> d = distractors(c.keyTerm, c.sentence, pool, 1, level, false);
            if (!d.isEmpty()) {
                String falseStatement = swapTerm(c.sentence, c.keyTerm, d.get(0));
                if (!falseStatement.equals(c.sentence)) {
                    return new QuizQuestion(Type.TRUE_FALSE, "True or False: " + falseStatement,
                            null, "False", level, c.sentence);
                }
            }
        }
        return new QuizQuestion(Type.TRUE_FALSE, "True or False: " + c.sentence,
                null, "True", level, c.sentence);
    }

    /** Not used while the quiz is MCQ-only. */
    private QuizQuestion buildShortAnswer(Candidate c, String level) {
        if (!c.isDefinition()) return null;
        String verb = c.defVerb.equals("are") ? "are" : "is";
        String q = level.equals("EASY")
                ? "What " + verb + " " + c.defTerm + "?"
                : "In your own words, what " + verb + " " + c.defTerm + "?";
        return new QuizQuestion(Type.SHORT_ANSWER, q, null, c.sentence, level, c.sentence);
    }

    // ------------------------------------------------------------------ distractors + quality filter

    /**
     * Words that appear in the same slot as the answer in OTHER sentences.
     * Example: "has _____ table" -> every word that precedes "table" elsewhere in the notes
     * (employee, department, ...). Only works for single-word answers.
     */
    private Set<String> contextTerms(String answer, String sentence, Pool pool) {
        Set<String> out = new LinkedHashSet<>();
        if (wordCount(answer) != 1) return out;
        String a = answer.toLowerCase();

        List<String> toks = lowerWords(sentence);
        int at = toks.indexOf(a);
        if (at < 0) return out;

        String left = at > 0 && isValidWord(toks.get(at - 1)) ? toks.get(at - 1) : null;
        String right = at < toks.size() - 1 && isValidWord(toks.get(at + 1)) ? toks.get(at + 1) : null;
        if (left == null && right == null) return out;

        for (String s : pool.sentences) {
            List<String> t = lowerWords(s);
            for (int i = 0; i < t.size(); i++) {
                String w = t.get(i);
                if (w.equals(a) || !isValidWord(w)) continue;
                if (pool.names.contains(w)) continue;   // never offer a person's name as an option
                boolean l = left != null && i > 0 && t.get(i - 1).equals(left);
                boolean r = right != null && i < t.size() - 1 && t.get(i + 1).equals(right);
                if ((l || r) && (pool.nouns.isEmpty() || pool.nouns.contains(w))) out.add(w);
            }
        }
        return out;
    }

    /**
     * Wrong options, best first:
     *  1. words from the same slot in other sentences (context terms) - only if allowContext
     *  2. other defined terms from the notes
     *  3. other key terms
     * Options have the same number of words as the answer (relaxed only if there are too few).
     * Easy prefers options that look different from the answer; Medium/Hard prefer look-alikes.
     * Casing follows the notes (DBMS stays DBMS) and matches the answer's style.
     */
    private List<String> distractors(String answer, String sentence, Pool pool, int n, String level,
                                     boolean allowContext) {
        String lowSentence = sentence.toLowerCase();
        String lowAnswer = answer.toLowerCase();
        int answerWords = wordCount(answer);
        Set<String> contextual = allowContext ? contextTerms(answer, sentence, pool) : new LinkedHashSet<String>();

        List<String> all = new ArrayList<>(contextual);
        all.addAll(pool.words);

        Comparator<String> byLength = level.equals("EASY")
                ? Comparator.comparingInt((String p) -> -Math.abs(p.length() - answer.length()))
                : Comparator.comparingInt((String p) -> Math.abs(p.length() - answer.length()));

        List<String> ordered = new ArrayList<>();
        for (boolean sameShape : new boolean[]{true, false}) {
            Set<String> seen = new HashSet<>();
            seen.add(stem(answer));   // "Tables" is not a different option from "table"
            List<String> ctx = new ArrayList<>(), pref = new ArrayList<>(), other = new ArrayList<>();
            for (String p : all) {
                String lp = p.toLowerCase();
                if (lp.equals(lowAnswer) || !seen.add(stem(lp))) continue;
                if (NameFilter.isName(p, pool.names)) continue;   // never a person's name
                if (lowSentence.contains(lp) || lowAnswer.contains(lp) || lp.contains(lowAnswer)) continue;
                if (sameShape && wordCount(p) != answerWords) continue;
                if (answer.length() >= 5 && p.length() < 4) continue;   // tiny words make poor options
                if (contextual.contains(lp)) ctx.add(p);
                else if (pool.preferred.contains(lp)) pref.add(p);
                else other.add(p);
            }
            ctx.sort(byLength);
            pref.sort(byLength);
            other.sort(byLength);
            ordered = new ArrayList<>();
            if (level.equals("EASY")) {            // easy: clearly different options
                ordered.addAll(other); ordered.addAll(pref); ordered.addAll(ctx);
            } else if (level.equals("HARD")) {     // hard: look-alike options from the same slot
                ordered.addAll(ctx); ordered.addAll(pref); ordered.addAll(other);
            } else {                             // medium: related terms first
                ordered.addAll(pref); ordered.addAll(ctx); ordered.addAll(other);
            }
            if (ordered.size() >= n) break;
        }

        int window = level.equals("HARD") ? n + 1 : n + 2;
        List<String> top = new ArrayList<>(ordered.subList(0, Math.min(ordered.size(), window)));
        Collections.shuffle(top, random);

        List<String> out = new ArrayList<>();
        for (String p : top.subList(0, Math.min(n, top.size()))) {
            out.add(matchCase(canonical(p, pool), answer));
        }
        return out;
    }

    /** Crude stem so "table" and "tables" count as the same option. */
    private String stem(String term) {
        String t = term.trim().toLowerCase();
        if (t.length() > 4 && t.endsWith("ies")) return t.substring(0, t.length() - 3) + "y";
        if (t.length() > 4 && t.matches(".*(s|x|z|ch|sh)es$")) return t.substring(0, t.length() - 2);
        if (t.length() > 3 && t.endsWith("s")) return t.substring(0, t.length() - 1);
        return t;
    }

    /** True if any word of the option is an acronym / mixed-case word such as "DBMS" or "iOS". */
    private boolean isAcronymLike(String option) {
        for (String token : option.split(" ")) {
            if (upperCount(token) >= 2) return true;
        }
        return false;
    }

    /**
     * Makes a wrong option look like the answer (same capitalisation) so the case does not give it away.
     * Acronyms keep the notes' own casing, so "DBMS" never becomes "Dbms".
     */
    private String matchCase(String option, String answer) {
        if (option.isEmpty() || answer.isEmpty()) return option;
        if (isAcronymLike(option)) return option;
        return Character.isUpperCase(answer.charAt(0)) ? upperFirst(option) : lowerFirst(option);
    }

    private boolean isGood(QuizQuestion q) {
        if (q == null || q.getQuestion() == null || q.getAnswer() == null) return false;

        int len = q.getQuestion().length();
        if (len < 15 || len > 350) return false;

        if (q.getOptions() != null &&
                new HashSet<>(q.getOptions()).size() != q.getOptions().size()) {
            return false;
        }

        if (q.getType() == Type.MCQ) {
            if (q.getOptions() == null || q.getOptions().size() != 4) return false;

            // Never allow an MCQ whose correct answer is not displayed.
            boolean answerPresent = q.getOptions().stream()
                    .anyMatch(o -> o != null &&
                            o.trim().equalsIgnoreCase(q.getAnswer().trim()));

            if (!answerPresent) return false;

            // Options that differ only by case are duplicates in disguise.
            Set<String> lower = new HashSet<>();
            for (String o : q.getOptions()) {
                if (o == null || !lower.add(o.trim().toLowerCase())) return false;
            }

            // The blank should not reveal the answer.
            return !q.getQuestion().toLowerCase()
                    .contains(q.getAnswer().toLowerCase());
        }

        if (q.getType() == Type.TRUE_FALSE) {
            return q.getAnswer().equalsIgnoreCase("True") ||
                   q.getAnswer().equalsIgnoreCase("False");
        }

        if (q.getType() == Type.FILL_BLANK) {
            return !q.getQuestion().toLowerCase()
                    .contains(q.getAnswer().toLowerCase());
        }

        return true;
    }
}