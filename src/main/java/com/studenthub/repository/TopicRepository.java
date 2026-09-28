package com.studenthub.repository;

import com.studenthub.model.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TopicRepository extends JpaRepository<Topic, Integer> {
    List<Topic> findBySubject_SubjectId(Integer subjectId);
}