package com.studenthub.controller;

import com.studenthub.dto.QuizQuestion;
import com.studenthub.dto.QuizSubmissionDTO;
import com.studenthub.service.QuizGeneratorService;
import com.studenthub.service.QuizService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizGeneratorService generator;
    private final QuizService quizService;

    public QuizController(QuizGeneratorService generator, QuizService quizService) {
        this.generator = generator;
        this.quizService = quizService;
    }

    public static class GenerateRequest {
        private String notes;
        private Integer count;
        private String difficulty;

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public String getDifficulty() { return difficulty; }
        public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    }

    // Frontend expects a plain JSON array of questions
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody GenerateRequest req) {
        try {
            int count = req.getCount() == null ? 10 : req.getCount();
            List<QuizQuestion> questions =
                    generator.generate(req.getNotes(), count, req.getDifficulty());
            return ResponseEntity.ok(questions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    // Server-side grading, saves the attempt and each answer to MySQL
    @PostMapping("/submit")
    public ResponseEntity<?> submit(@RequestBody QuizSubmissionDTO submission) {
        try {
            return ResponseEntity.ok(quizService.submitQuiz(submission));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("error", e.getMessage()));
        }
    }
}