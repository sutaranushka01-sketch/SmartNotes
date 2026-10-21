package com.studenthub.service;

import java.util.regex.Pattern;

/** Removes worked examples, scenarios and code samples from notes. */
public final class ExampleFilter {

    private ExampleFilter() {}

    // A line that is only a heading: "Example", "Examples:", "Example 2 -", "For example:"
    private static final Pattern EXAMPLE_HEADING = Pattern.compile(
        "^(for\\s+)?(example|examples|sample|samples|illustration|output|input)\\s*\\d*\\s*[:.\\-)]*\\s*$",
        Pattern.CASE_INSENSITIVE);

    // A line that starts an example: "Example: ...", "e.g. ...", "For instance, ..."
    private static final Pattern EXAMPLE_START = Pattern.compile(
        "^(for\\s+(example|instance)|example|examples|sample|e\\.g\\.?|eg\\s*[:.]|ex\\s*[:.]"
        + "|consider|suppose|imagine|assume|let'?s\\s+(say|take|consider)|say\\s+we|take\\s+the\\s+case)\\b.*",
        Pattern.CASE_INSENSITIVE);

    // Example markers anywhere in a sentence: "Example:", "for example", "e.g.", "such as"
    private static final Pattern EXAMPLE_INSIDE = Pattern.compile(
        "\\bexamples?\\s*[:\\-]"
        + "|(\\(|,|;|\\s)(for example|for instance|e\\.g\\.?|such as)\\b",
        Pattern.CASE_INSENSITIVE);

    // SQL / code lines (only things that really look like code, not definitions)
    private static final Pattern CODE_LINE = Pattern.compile(
        ".*;\\s*$"
        + "|.*\\bselect\\s+\\*.*"
        + "|.*\\b(insert\\s+into|delete\\s+from|create\\s+table)\\s+\\w+\\s*\\(.*",
        Pattern.CASE_INSENSITIVE);

    private static final int MAX_BLOCK_LINES = 6;

    /** Removes bullet symbols, hidden characters and spaces from the start (used only for checking). */
    private static String clean(String s) {
        return s.replaceFirst("^[^\\p{L}\\p{N}]+", "").trim();
    }

    /** Call on the whole notes text before splitting into sentences. */
    public static String removeExampleBlocks(String notes) {
        if (notes == null) return "";
        StringBuilder out = new StringBuilder();
        boolean inExample = false;
        int skipped = 0;

        for (String raw : notes.split("\\R")) {
            String line = clean(raw);

            if (line.isEmpty()) {
                inExample = false;
                skipped = 0;
                out.append('\n');
                continue;
            }
            if (EXAMPLE_HEADING.matcher(line).matches() || EXAMPLE_START.matcher(line).matches()) {
                inExample = true;
                skipped = 1;
                continue;
            }
            if (inExample) {
                if (++skipped > MAX_BLOCK_LINES) {
                    inExample = false;      // safety: don't swallow the whole file
                } else {
                    continue;
                }
            }
            if (CODE_LINE.matcher(line).matches()) continue;
            out.append(raw).append('\n');
        }
        return out.toString();
    }

    /** Call on every sentence before using it for a question, option or flashcard. */
    public static boolean isExample(String sentence) {
        if (sentence == null) return true;
        String s = clean(sentence);
        return EXAMPLE_HEADING.matcher(s).matches()
            || EXAMPLE_START.matcher(s).matches()
            || EXAMPLE_INSIDE.matcher(s).find()
            || CODE_LINE.matcher(s).matches();
    }
}