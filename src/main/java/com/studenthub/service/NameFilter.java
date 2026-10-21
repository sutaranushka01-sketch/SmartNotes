package com.studenthub.service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds person names in the notes so they are never used as defined terms,
 * flashcard fronts, quiz answers or wrong options ("What is Amit?").
 *
 * A word is treated as a name when:
 *  - it is a well-known first name (COMMON_NAMES), or
 *  - it is written Title-case, its lowercase form never appears anywhere in the notes
 *    (so it is not a common noun like "database"), and it does not look like a
 *    noun ("normalization", "management", ...).
 * All-caps words (SQL, DBMS) and mixed-case words (MySQL) are never names.
 */
public final class NameFilter {

    private NameFilter() {}

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z]+");

    // Unambiguous first names only (avoid words like "mark", "will", "rose", "grace").
    private static final Set<String> COMMON_NAMES = new HashSet<>(Arrays.asList(
            "amit", "rahul", "rohan", "rohit", "priya", "pooja", "neha", "sneha", "anjali", "ankit",
            "arjun", "karan", "vikram", "vijay", "suresh", "ramesh", "mahesh", "sachin", "sanjay",
            "ajay", "akash", "aman", "ravi", "raj", "sunil", "anil", "deepak", "manish", "nikhil",
            "varun", "yash", "harsh", "kunal", "gaurav", "mohit", "sumit", "vishal", "abhishek",
            "aditya", "siddharth", "shubham", "pranav", "kavya", "riya", "isha", "divya", "shreya",
            "sakshi", "komal", "swati", "nisha", "meera", "sita", "gita", "radha", "kiran", "anita",
            "sunita", "rekha", "asha", "ayesha", "fatima", "zoya", "imran", "irfan", "salman",
            "john", "david", "michael", "james", "robert", "peter", "paul", "mary", "alice", "emma",
            "sarah", "jennifer", "jessica", "daniel", "thomas", "george", "henry", "oliver", "sophia"));

    // Capitalised technical words that are NOT people.
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "java", "python", "oracle", "linux", "windows", "unix", "excel", "access", "android",
            "google", "microsoft", "apple", "amazon", "ubuntu", "mongodb", "postgres", "sqlite",
            "boolean", "codd", "chen"));

    private static final String[] NOUN_SUFFIXES = {
            "tion", "sion", "ment", "ness", "ity", "ing", "ism", "ogy", "ics", "ance", "ence",
            "base", "ship", "ure", "ware", "work", "table", "key", "model", "system"};

    private static boolean isTitleCase(String w) {
        return w.length() >= 2 && Character.isUpperCase(w.charAt(0))
                && w.substring(1).equals(w.substring(1).toLowerCase());
    }

    private static boolean looksLikeNoun(String w) {
        for (String s : NOUN_SUFFIXES) if (w.endsWith(s)) return true;
        return false;
    }

    /** Scan the WHOLE notes once. Returns lowercase names. */
    public static Set<String> findNames(String notes) {
        Set<String> lower = new HashSet<>();
        Set<String> title = new LinkedHashSet<>();
        Matcher m = TOKEN.matcher(notes == null ? "" : notes);
        while (m.find()) {
            String w = m.group();
            if (w.equals(w.toLowerCase())) lower.add(w);
            else if (isTitleCase(w)) title.add(w.toLowerCase());
        }
        Set<String> names = new HashSet<>();
        for (String w : title) {
            if (ALLOWED.contains(w)) continue;
            if (COMMON_NAMES.contains(w)) { names.add(w); continue; }
            if (w.length() < 3 || lower.contains(w) || looksLikeNoun(w)) continue;
            names.add(w);
        }
        return names;
    }

    /** True when every word of the term is a name ("Amit", "Amit Sharma"). */
    public static boolean isName(String term, Set<String> names) {
        if (term == null || names == null || names.isEmpty()) return false;
        Matcher m = TOKEN.matcher(term);
        int count = 0;
        while (m.find()) {
            if (!names.contains(m.group().toLowerCase())) return false;
            count++;
        }
        return count > 0;
    }

    /** True when the sentence is ABOUT a person: it starts with a name ("Amit is a student of CS."). */
    public static boolean startsWithName(String sentence, Set<String> names) {
        if (sentence == null || names == null || names.isEmpty()) return false;
        Matcher m = TOKEN.matcher(sentence);
        return m.find() && names.contains(m.group().toLowerCase());
    }
}