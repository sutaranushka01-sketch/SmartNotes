package com.studenthub.controller;

import com.studenthub.model.Subject;
import com.studenthub.model.Topic;
import com.studenthub.repository.SubjectRepository;
import com.studenthub.repository.TopicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/topics")
@CrossOrigin(origins = "*")
public class TopicController {

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    // Get all topics for a given subject
    @GetMapping("/{subjectId}")
    public List<Topic> getTopicsBySubject(@PathVariable Integer subjectId) {
        return topicRepository.findBySubject_SubjectId(subjectId);
    }

    // Add a new topic under a subject
    @PostMapping("/{subjectId}")
    public Topic addTopic(@PathVariable Integer subjectId, @RequestBody Topic topic) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new RuntimeException("Subject not found"));
        topic.setSubject(subject);
        return topicRepository.save(topic);
    }
}