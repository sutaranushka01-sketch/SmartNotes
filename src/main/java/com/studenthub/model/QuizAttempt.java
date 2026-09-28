package com.studenthub.model;

import jakarta.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name = "quiz_attempt")
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer attemptId;

    private Integer studentId;

    @ManyToOne
    @JoinColumn(name = "topic_id")
    private Topic topic;

    private Integer score;
    private Integer totalQuestions;
    private Double percentage;

    @Column(insertable = false, updatable = false)
    private Timestamp attemptDate;

    // Getters and Setters
    public Integer getAttemptId() { return attemptId; }
    public void setAttemptId(Integer attemptId) { this.attemptId = attemptId; }

    public Integer getStudentId() { return studentId; }
    public void setStudentId(Integer studentId) { this.studentId = studentId; }

    public Topic getTopic() { return topic; }
    public void setTopic(Topic topic) { this.topic = topic; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }

    public Double getPercentage() { return percentage; }
    public void setPercentage(Double percentage) { this.percentage = percentage; }

    public Timestamp getAttemptDate() { return attemptDate; }
}