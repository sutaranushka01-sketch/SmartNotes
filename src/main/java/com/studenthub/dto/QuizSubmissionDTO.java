package com.studenthub.dto;

import java.util.List;

public class QuizSubmissionDTO {
    private Integer studentId;
    private Integer topicId;
    private List<AnswerSubmissionDTO> answers;

    public Integer getStudentId() { return studentId; }
    public void setStudentId(Integer studentId) { this.studentId = studentId; }

    public Integer getTopicId() { return topicId; }
    public void setTopicId(Integer topicId) { this.topicId = topicId; }

    public List<AnswerSubmissionDTO> getAnswers() { return answers; }
    public void setAnswers(List<AnswerSubmissionDTO> answers) { this.answers = answers; }
}