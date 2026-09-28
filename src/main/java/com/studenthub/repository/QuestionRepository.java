package com.studenthub.repository;

import com.studenthub.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Integer> {
    List<Question> findByTopic_TopicId(Integer topicId);
}