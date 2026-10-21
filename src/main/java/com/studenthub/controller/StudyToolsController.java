package com.studenthub.controller;

import com.studenthub.service.StudyToolsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/api/study")
public class StudyToolsController {

    private final StudyToolsService study;

    public StudyToolsController(StudyToolsService study) {
        this.study = study;
    }

    public static class NotesRequest {
        private String notes;
        private Integer count;   // optional: summary length / number of flashcards

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
    }

    @PostMapping("/summary")
    public ResponseEntity<?> summary(@RequestBody NotesRequest req) {
        try {
            return ResponseEntity.ok(study.summarize(req.getNotes(), req.getCount()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    @PostMapping("/flashcards")
    public ResponseEntity<?> flashcards(@RequestBody NotesRequest req) {
        try {
            return ResponseEntity.ok(study.flashcards(req.getNotes(), req.getCount()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }
}