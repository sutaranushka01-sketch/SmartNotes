package com.studenthub.repository;

import com.studenthub.model.Notes;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotesRepository extends JpaRepository<Notes, Integer> {
    List<Notes> findByTopic_TopicId(Integer topicId);
}