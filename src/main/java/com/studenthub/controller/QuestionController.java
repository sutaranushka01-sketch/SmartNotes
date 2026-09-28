package com.studenthub.controller;

import com.studenthub.dto.QuestionDTO;
import com.studenthub.model.Question;
import com.studenthub.model.Topic;
import com.studenthub.repository.QuestionRepository;
import com.studenthub.repository.TopicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/questions")
@CrossOrigin(origins = "*")
public class QuestionController {

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private TopicRepository topicRepository;

    // STUDENT-FACING: returns questions WITHOUT the correct answer
    @GetMapping("/{topicId}")
    public List<QuestionDTO> getQuestionsForQuiz(@PathVariable Integer topicId) {
        List<Question> questions = questionRepository.findByTopic_TopicId(topicId);

        return questions.stream()
                .map(q -> new QuestionDTO(
                        q.getQuestionId(),
                        q.getQuestionText(),
                        q.getOptionA(),
                        q.getOptionB(),
                        q.getOptionC(),
                        q.getOptionD(),
                        q.getQuestionType()
                ))
                .collect(Collectors.toList());
    }

    // ADMIN-FACING: add a new question (includes correct answer)
    @PostMapping("/{topicId}")
    public Question addQuestion(@PathVariable Integer topicId, @RequestBody Question question) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new RuntimeException("Topic not found"));
        question.setTopic(topic);
        return questionRepository.save(question);
    }
}