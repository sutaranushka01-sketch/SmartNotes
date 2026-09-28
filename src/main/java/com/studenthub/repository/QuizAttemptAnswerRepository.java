package com.studenthub.repository;

import com.studenthub.model.QuizAttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, Integer> {
}