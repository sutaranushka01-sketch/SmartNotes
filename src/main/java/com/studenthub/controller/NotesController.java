package com.studenthub.controller;

import com.studenthub.model.Notes;
import com.studenthub.model.Topic;
import com.studenthub.repository.NotesRepository;
import com.studenthub.repository.TopicRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notes")
@CrossOrigin(origins = "*")
public class NotesController {

    @Autowired
    private NotesRepository notesRepository;

    @Autowired
    private TopicRepository topicRepository;

    // Get all notes for a given topic
    @GetMapping("/{topicId}")
    public List<Notes> getNotesByTopic(@PathVariable Integer topicId) {
        return notesRepository.findByTopic_TopicId(topicId);
    }

    // Add notes as plain text (typed or pasted)
    @PostMapping("/{topicId}")
    public Notes addNotes(@PathVariable Integer topicId, @RequestBody Notes notes) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new RuntimeException("Topic not found"));
        notes.setTopic(topic);
        return notesRepository.save(notes);
    }

    // Add notes by uploading a PDF — text is extracted AND saved against a topic
    @PostMapping("/upload-pdf/{topicId}")
    public Notes uploadPdfNotes(@PathVariable Integer topicId, @RequestParam("file") MultipartFile file) throws IOException {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new RuntimeException("Topic not found"));

        String extractedText;
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            extractedText = stripper.getText(document);
        }

        Notes notes = new Notes();
        notes.setContent(extractedText);
        notes.setTopic(topic);
        return notesRepository.save(notes);
    }

    // Extract text from a PDF WITHOUT saving anything — used by the dropzone
    // before a topic exists yet (e.g. before "Generate quiz" is clicked)
    @PostMapping("/extract-pdf")
    public Map<String, String> extractPdfText(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("No file provided");
        }
        String extractedText;
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            if (document.isEncrypted()) {
                throw new RuntimeException("PDF is password protected");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            extractedText = stripper.getText(document);
        }
        return Map.of("text", extractedText.trim());
    }
}