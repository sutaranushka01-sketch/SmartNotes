package com.studenthub.dto;

public class AuthDTOs {

    public static class RegisterRequest {
        public String name;
        public String email;
        public String password;
        public String course;
    }

    public static class LoginRequest {
        public String email;
        public String password;
    }

    public static class AuthResponse {
        public Integer studentId;
        public String name;
        public String email;
        public String course;

        public AuthResponse(Integer studentId, String name, String email, String course) {
            this.studentId = studentId;
            this.name = name;
            this.email = email;
            this.course = course;
        }
    }
}
