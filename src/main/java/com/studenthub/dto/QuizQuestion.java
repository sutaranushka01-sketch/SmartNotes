package com.studenthub.dto;

import java.util.List;

public class QuizQuestion {

    public enum Type { FILL_BLANK, MCQ, TRUE_FALSE, SHORT_ANSWER }

    private final Type type;
    private final String question;
    private final List<String> options;   // only for MCQ, otherwise null
    private final String answer;
    private final String difficulty;      // EASY, MEDIUM, HARD
    private final String sourceSentence;  // shown as explanation after answering

    public QuizQuestion(Type type, String question, List<String> options,
                        String answer, String difficulty, String sourceSentence) {
        this.type = type;
        this.question = question;
        this.options = options;
        this.answer = answer;
        this.difficulty = difficulty;
        this.sourceSentence = sourceSentence;
    }

    public Type getType() { return type; }
    public String getQuestion() { return question; }
    public List<String> getOptions() { return options; }
    public String getAnswer() { return answer; }
    public String getDifficulty() { return difficulty; }
    public String getSourceSentence() { return sourceSentence; }
}
