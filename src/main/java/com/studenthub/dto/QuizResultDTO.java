package com.studenthub.dto;

public class QuizResultDTO {
    private Integer attemptId;
    private Integer score;
    private Integer totalQuestions;
    private Double percentage;

    public QuizResultDTO(Integer attemptId, Integer score, Integer totalQuestions, Double percentage) {
        this.attemptId = attemptId;
        this.score = score;
        this.totalQuestions = totalQuestions;
        this.percentage = percentage;
    }

    public Integer getAttemptId() { return attemptId; }
    public Integer getScore() { return score; }
    public Integer getTotalQuestions() { return totalQuestions; }
    public Double getPercentage() { return percentage; }
}