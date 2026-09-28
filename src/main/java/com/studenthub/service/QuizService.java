package com.studenthub.service;

import com.studenthub.dto.*;
import com.studenthub.model.*;
import com.studenthub.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuizService {

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private QuizAttemptAnswerRepository quizAttemptAnswerRepository;

    @Autowired
    private TopicRepository topicRepository;

    public QuizResultDTO submitQuiz(QuizSubmissionDTO submission) {

        List<AnswerSubmissionDTO> studentAnswers = submission.getAnswers();
        int totalQuestions = studentAnswers.size();
        int score = 0;

        // Step A: Create the attempt record first (score updated after loop)
        Topic topic = topicRepository.findById(submission.getTopicId())
                .orElseThrow(() -> new RuntimeException("Topic not found"));

        QuizAttempt attempt = new QuizAttempt();
        attempt.setStudentId(submission.getStudentId());
        attempt.setTopic(topic);
        attempt.setTotalQuestions(totalQuestions);
        attempt.setScore(0);        // placeholder, updated below
        attempt.setPercentage(0.0); // placeholder, updated below
        attempt = quizAttemptRepository.save(attempt);

        // Step B: Check each answer against the DB's correct answer
        for (AnswerSubmissionDTO studentAnswer : studentAnswers) {

            Question question = questionRepository.findById(studentAnswer.getQuestionId())
                    .orElseThrow(() -> new RuntimeException("Question not found"));

            boolean isCorrect = isAnswerCorrect(question, studentAnswer.getSelectedOption());

            if (isCorrect) {
                score++;
            }

            // Save each individual answer for later review
            QuizAttemptAnswer answerRecord = new QuizAttemptAnswer();
            answerRecord.setAttempt(attempt);
            answerRecord.setQuestion(question);
            answerRecord.setSelectedOption(studentAnswer.getSelectedOption());
            answerRecord.setIsCorrect(isCorrect);
            quizAttemptAnswerRepository.save(answerRecord);
        }

        // Step C: Calculate percentage and update the attempt record
        double percentage = ((double) score / totalQuestions) * 100;
        attempt.setScore(score);
        attempt.setPercentage(percentage);
        quizAttemptRepository.save(attempt);

        return new QuizResultDTO(attempt.getAttemptId(), score, totalQuestions, percentage);
    }

    // ---- grading helpers -------------------------------------------------
    private boolean isAnswerCorrect(Question q, String given) {
        if (given == null || given.isBlank()) return false;
        if ("short".equals(q.getQuestionType())) {
            return shortAnswerMatches(q.getCorrectAnswer(), given);
        }
        return q.getCorrectOption() != null && q.getCorrectOption().equalsIgnoreCase(given.trim());
    }

    private static final java.util.Set<String> FILLER = java.util.Set.of(
            "a", "an", "the", "is", "are", "of", "to", "in", "it", "its", "and");

    private static java.util.List<String> tokens(String s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (s == null) return out;
        for (String t : s.toLowerCase().replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+")) {
            if (!t.isEmpty() && !FILLER.contains(t)) out.add(t);
        }
        return out;
    }

    // Lenient match: same wording, one contains the other, or >= 60% of expected words present.
    // Keep this in sync with shortMatches() in index.html.
    static boolean shortAnswerMatches(String expected, String given) {
        java.util.List<String> e = tokens(expected), g = tokens(given);
        if (e.isEmpty() || g.isEmpty()) return false;
        String es = String.join(" ", e), gs = String.join(" ", g);
        if (es.equals(gs)) return true;
        if (gs.length() >= 3 && (es.contains(gs) || gs.contains(es))) return true;
        long hit = e.stream().filter(g::contains).count();
        return hit >= Math.ceil(e.size() * 0.6);
    }
}
