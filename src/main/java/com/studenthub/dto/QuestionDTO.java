package com.studenthub.dto;

public class QuestionDTO {

    private Integer questionId;
    private String questionText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String questionType;

    public QuestionDTO(Integer questionId, String questionText, String optionA,
                        String optionB, String optionC, String optionD,
                        String questionType) {
        this.questionId = questionId;
        this.questionText = questionText;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.questionType = questionType == null ? "mcq" : questionType;
    }

    public Integer getQuestionId() { return questionId; }
    public String getQuestionText() { return questionText; }
    public String getOptionA() { return optionA; }
    public String getOptionB() { return optionB; }
    public String getOptionC() { return optionC; }
    public String getOptionD() { return optionD; }
    public String getQuestionType() { return questionType; }
}