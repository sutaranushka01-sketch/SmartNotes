package com.studenthub.controller;

import com.studenthub.dto.QuizResultDTO;
import com.studenthub.dto.QuizSubmissionDTO;
import com.studenthub.model.QuizAttempt;
import com.studenthub.repository.QuizAttemptRepository;
import com.studenthub.service.QuizService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quiz")
@CrossOrigin(origins = "*")
public class QuizController {

    @Autowired
    private QuizService quizService;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    // Submit quiz answers, get score back
    @PostMapping("/submit")
    public QuizResultDTO submitQuiz(@RequestBody QuizSubmissionDTO submission) {
        return quizService.submitQuiz(submission);
    }

    // View past attempts for a student
    @GetMapping("/history/{studentId}")
    public List<QuizAttempt> getHistory(@PathVariable Integer studentId) {
        return quizAttemptRepository.findByStudentId(studentId);
    }
}