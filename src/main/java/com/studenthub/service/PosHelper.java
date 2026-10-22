package com.studenthub.service;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.SimpleTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Part-of-speech helper built on Apache OpenNLP (classic statistical NLP, runs offline, no AI service).
 *
 * It tells the quiz / flashcard generators which words are NOUNS, ADJECTIVES and VERBS, so they can:
 *  - blank only real terms (nouns / noun phrases such as "primary key"), never words like "easy"
 *  - build wrong options of the same kind (nouns), not random words
 *  - reject fragments that have no verb, and tasks that start with a command verb
 *
 * If the OpenNLP model is missing, available() is false and every method returns a neutral answer,
 * so the generators silently fall back to their plain rule-based behaviour.
 *
 * Works with both tag sets: Penn Treebank (NN, VBZ, JJ ...) and Universal (NOUN, VERB, ADJ, AUX ...).
 */
public final class PosHelper {

    private static final Logger log = LoggerFactory.getLogger(PosHelper.class);
    private static final Pattern ALPHA = Pattern.compile("[A-Za-z][A-Za-z0-9]*");

    /** Guards model loading only. */
    private static final Object LOCK = new Object();
    /** Guards the (non-thread-safe) tagger and tokenizer while tagging. */
    private static final Object TAG_LOCK = new Object();

    private static final Map<String, Tagged> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, Tagged>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Tagged> eldest) {
                    return size() > 3000;
                }
            });

    private static volatile POSTaggerME tagger;
    private static volatile boolean initDone;

    private PosHelper() {}

    /** A tokenised, tagged sentence. */
    public static final class Tagged {
        public final String[] tokens;
        public final String[] tags;

        Tagged(String[] tokens, String[] tags) {
            this.tokens = tokens;
            this.tags = tags;
        }
    }

    // ------------------------------------------------------------------ loading

    public static boolean available() {
        if (!initDone) init();          // fast path: no lock once loaded
        return tagger != null;
    }

    /**
     * Looks for an English POS model anywhere on the classpath, so it works with the Maven model jar
     * (opennlp-models-pos-en) AND with a .bin file placed in src/main/resources/models/.
     */
    private static void init() {
        synchronized (LOCK) {
            if (initDone) return;
            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                String[] patterns = {
                        "classpath*:opennlp-*pos*.bin",
                        "classpath*:models/*pos*.bin",
                        "classpath*:*pos*maxent*.bin",
                        "classpath*:*pos*.bin"
                };
                Resource chosen = null;
                for (String p : patterns) {
                    chosen = pickEnglish(resolver.getResources(p));
                    if (chosen != null) break;
                }
                if (chosen == null) {
                    log.warn("No OpenNLP POS model found on the classpath - using rule-only mode. "
                            + "Add opennlp-models-pos-en or put an English *pos*.bin in src/main/resources/models/.");
                    return;
                }
                try (InputStream in = chosen.getInputStream()) {
                    tagger = new POSTaggerME(new POSModel(in));
                }
                log.info("OpenNLP POS tagger loaded from {}", chosen.getFilename());
            } catch (Exception | LinkageError e) {
                tagger = null;
                log.warn("Could not load the OpenNLP POS model - using rule-only mode: {}", e.toString());
            } finally {
                initDone = true;        // set LAST, so other threads never see "done" before the tagger is set
            }
        }
    }

    private static Resource pickEnglish(Resource[] found) {
        if (found == null || found.length == 0) return null;
        for (Resource r : found) {
            String n = r.getFilename() == null ? "" : r.getFilename().toLowerCase();
            if (n.startsWith("en-") || n.contains("-en-") || n.contains("_en_")) return r;
        }
        return found[0];
    }

    // ------------------------------------------------------------------ tagging

    public static Tagged tag(String sentence) {
        if (sentence == null || !available()) return null;
        Tagged cached = CACHE.get(sentence);
        if (cached != null) return cached;
        try {
            Tagged result;
            synchronized (TAG_LOCK) {
                String[] tokens = SimpleTokenizer.INSTANCE.tokenize(sentence);
                if (tokens.length == 0) return null;
                result = new Tagged(tokens, tagger.tag(tokens));
            }
            CACHE.put(sentence, result);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ tag helpers

    public static boolean isNoun(String tag) {
        return tag != null && (tag.startsWith("NN") || tag.equals("NOUN") || tag.equals("PROPN"));
    }

    public static boolean isAdj(String tag) {
        return tag != null && (tag.startsWith("JJ") || tag.equals("ADJ"));
    }

    /** A verb that can carry a sentence ("is", "stores", "can") - not a bare gerund like "running". */
    private static boolean isFiniteVerb(String tag, String token) {
        if (tag == null) return false;
        switch (tag) {
            case "VBZ": case "VBP": case "VBD": case "MD": case "AUX":
                return true;
            case "VERB":
                return !token.toLowerCase().endsWith("ing");
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------ sentence checks

    /**
     * True when the sentence has a noun and a real verb and does not start with a command verb.
     * Returns true when no model is loaded (no information = do not block anything).
     */
    public static boolean looksLikeStatement(String sentence) {
        Tagged t = tag(sentence);
        if (t == null) return true;

        boolean noun = false, finite = false;
        for (int i = 0; i < t.tokens.length; i++) {
            if (isNoun(t.tags[i])) noun = true;
            if (isFiniteVerb(t.tags[i], t.tokens[i])) finite = true;
        }
        if (!noun || !finite) return false;

        String firstTag = t.tags[0];
        String first = t.tokens[0].toLowerCase();
        boolean imperative = ("VB".equals(firstTag) || "VERB".equals(firstTag)) && !first.endsWith("ing");
        return !imperative;
    }

    /** True when the text looks like a noun phrase ("primary key"), not a clause or a fragment. */
    public static boolean isNounPhrase(String term) {
        Tagged t = tag(term);
        if (t == null) return true;
        if (!isNoun(t.tags[t.tags.length - 1])) return false;
        for (int i = 0; i < t.tokens.length; i++) {
            if (isFiniteVerb(t.tags[i], t.tokens[i])) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ noun phrases

    /** Runs of adjectives/nouns that end with a noun, as [start, end) token ranges. */
    private static List<int[]> runs(Tagged t) {
        List<int[]> out = new ArrayList<>();
        int n = t.tokens.length, i = 0;
        while (i < n) {
            if (!inRun(t, i)) { i++; continue; }
            int j = i;
            while (j < n && inRun(t, j)) j++;
            int end = j;
            while (end > i && !isNoun(t.tags[end - 1])) end--;   // a phrase must end with a noun
            if (end > i) out.add(new int[]{i, end});
            i = j;
        }
        return out;
    }

    private static boolean inRun(Tagged t, int i) {
        String tag = t.tags[i];
        return (isNoun(tag) || isAdj(tag)) && ALPHA.matcher(t.tokens[i]).matches();
    }

    private static int skipUselessStart(Tagged t, int start, int end, Predicate<String> valid, Set<String> bad) {
        while (start < end) {
            String w = t.tokens[start].toLowerCase();
            if (valid.test(w) && !bad.contains(w)) break;
            start++;
        }
        return start;
    }

    private static String join(Tagged t, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(t.tokens[i]);
        }
        return sb.toString();
    }

    /**
     * The best term to ask about in this sentence: the noun or noun phrase (up to 3 words) with the
     * highest keyword score, e.g. "primary key". Returns null when there is none or no model is loaded.
     */
    public static String bestTerm(String sentence, Map<String, Double> scores,
                                  Predicate<String> valid, Set<String> bad) {
        Tagged t = tag(sentence);
        if (t == null) return null;
        String lowSentence = sentence.toLowerCase();

        String best = null;
        double bestScore = 0;
        for (int[] run : runs(t)) {
            int end = run[1];
            int start = skipUselessStart(t, Math.max(run[0], end - 3), end, valid, bad);
            if (start >= end) continue;

            String text = join(t, start, end);
            if (!lowSentence.contains(text.toLowerCase())) {   // tokens must match the real text
                start = end - 1;
                text = t.tokens[start];
            }
            String last = t.tokens[end - 1].toLowerCase();
            if (!valid.test(last) || bad.contains(last)) continue;

            double score = 0;
            for (int i = start; i < end; i++) {
                score += scores.getOrDefault(t.tokens[i].toLowerCase(), 0.0);
            }
            if (score > bestScore) {
                bestScore = score;
                best = text;
            }
        }
        return best;
    }

    /** Collects every noun and every 2-3 word noun phrase of the notes (all lower case). */
    public static void collectTerms(List<String> sentences, Predicate<String> valid, Set<String> bad,
                                    Set<String> nouns, Set<String> phrases) {
        if (!available()) return;
        for (String s : sentences) {
            Tagged t = tag(s);
            if (t == null) continue;
            String lowSentence = s.toLowerCase();

            for (int i = 0; i < t.tokens.length; i++) {
                String w = t.tokens[i].toLowerCase();
                if (isNoun(t.tags[i]) && valid.test(w) && !bad.contains(w)) nouns.add(w);
            }
            for (int[] run : runs(t)) {
                int end = run[1];
                for (int len = 2; len <= 3; len++) {
                    int start = end - len;
                    if (start < run[0]) break;
                    if (skipUselessStart(t, start, end, valid, bad) != start) continue;
                    String text = join(t, start, end).toLowerCase();
                    if (lowSentence.contains(text)) phrases.add(text);
                }
            }
        }
    }
}