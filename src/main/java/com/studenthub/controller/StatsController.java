package com.studenthub.controller;

import com.studenthub.model.QuizAttempt;
import com.studenthub.repository.QuizAttemptRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final QuizAttemptRepository attempts;

    public StatsController(QuizAttemptRepository attempts) {
        this.attempts = attempts;
    }

    private LocalDate dayOf(QuizAttempt a) {
        return a.getAttemptDate() == null ? null
                : a.getAttemptDate().toLocalDateTime().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private double pct(QuizAttempt a) {
        return a.getPercentage() == null ? 0 : a.getPercentage();
    }

    @GetMapping("/{studentId}")
    public Map<String, Object> stats(@PathVariable Integer studentId) {
        List<QuizAttempt> all = new ArrayList<>(attempts.findByStudentId(studentId));
        all.sort((x, y) -> Integer.compare(y.getAttemptId(), x.getAttemptId())); // newest first

        LocalDate today = LocalDate.now();
        double avg = all.stream().mapToDouble(this::pct).average().orElse(0);

        // streak: consecutive days with at least one quiz (today or yesterday as the start)
        Set<LocalDate> days = all.stream().map(this::dayOf).filter(Objects::nonNull).collect(Collectors.toSet());
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (days.contains(cursor)) { streak++; cursor = cursor.minusDays(1); }

        // last 7 days
        List<Map<String, Object>> week = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            List<QuizAttempt> dayList = all.stream().filter(a -> d.equals(dayOf(a))).collect(Collectors.toList());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            m.put("quizzes", dayList.size());
            m.put("avg", Math.round(dayList.stream().mapToDouble(this::pct).average().orElse(0)));
            m.put("today", i == 0);
            week.add(m);
        }

        // per-topic average (grouped by topic name)
        Map<String, List<QuizAttempt>> byTopic = new LinkedHashMap<>();
        for (QuizAttempt a : all) {
            String name = a.getTopic() == null ? "Untitled" : a.getTopic().getTopicName();
            byTopic.computeIfAbsent(name, k -> new ArrayList<>()).add(a);
        }
        List<Map<String, Object>> topics = new ArrayList<>();
        byTopic.forEach((name, list) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("attempts", list.size());
            m.put("average", list.stream().mapToDouble(this::pct).average().orElse(0));
            topics.add(m);
        });
        topics.sort((x, y) -> Double.compare((double) y.get("average"), (double) x.get("average")));

        // recent attempts
        List<Map<String, Object>> recent = all.stream().limit(30).map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("attemptId", a.getAttemptId());
            m.put("topic", a.getTopic() == null ? "Untitled" : a.getTopic().getTopicName());
            m.put("score", a.getScore());
            m.put("totalQuestions", a.getTotalQuestions());
            m.put("percentage", pct(a));
            m.put("date", a.getAttemptDate() == null ? null : a.getAttemptDate().toLocalDateTime().toString());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("quizzesTaken", all.size());
        out.put("averageScore", avg);
        out.put("streak", streak);
        out.put("week", week);
        out.put("topics", topics);
        out.put("recent", recent);
        return out;
    }
}