package com.studenthub.controller;

import com.studenthub.dto.AuthDTOs.*;
import com.studenthub.model.Student;
import com.studenthub.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private StudentRepository studentRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (req.name == null || req.name.isBlank()
                || req.email == null || req.email.isBlank()
                || req.password == null || req.password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Name, a valid email, and a password of at least 6 characters are required."));
        }

        String email = req.email.trim().toLowerCase();

        if (studentRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "An account with this email already exists."));
        }

        Student student = new Student();
        student.setName(req.name.trim());
        student.setEmail(email);
        student.setPassword(encoder.encode(req.password));
        student.setCourse(req.course);

        Student saved = studentRepository.save(student);
        return ResponseEntity.ok(new AuthResponse(saved.getStudentId(), saved.getName(), saved.getEmail(), saved.getCourse()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (req.email == null || req.password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required."));
        }

        String email = req.email.trim().toLowerCase();

        return studentRepository.findByEmail(email)
                .filter(student -> encoder.matches(req.password, student.getPassword()))
                .<ResponseEntity<?>>map(student -> ResponseEntity.ok(
                        new AuthResponse(student.getStudentId(), student.getName(), student.getEmail(), student.getCourse())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid email or password.")));
    }
}
