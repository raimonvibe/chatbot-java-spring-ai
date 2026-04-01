package com.prayer_chat.chatbot.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

/**
 * Validator implementation for @NotCommonPassword annotation
 * Rejects commonly used passwords
 */
public class NotCommonPasswordValidator implements ConstraintValidator<NotCommonPassword, String> {

    // List of common password patterns (case-insensitive)
    private static final Set<String> COMMON_PASSWORDS = Set.of(
        "password", "password123", "password1",
        "welcome", "welcome123",
        "admin", "admin123",
        "qwerty", "qwerty123",
        "123456", "12345678",
        "letmein", "monkey",
        "dragon", "baseball",
        "iloveyou", "trustno1",
        "sunshine", "princess",
        "abc123", "football"
    );

    @Override
    public void initialize(NotCommonPassword constraintAnnotation) {
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        // Null values are handled by @NotBlank/@NotNull annotations
        if (password == null || password.trim().isEmpty()) {
            return true;
        }

        // Check if password (case-insensitive) contains common patterns
        String lowerPassword = password.toLowerCase();

        // Check exact match first
        if (COMMON_PASSWORDS.contains(lowerPassword)) {
            return false;
        }

        // Check if password starts with a common password pattern
        // This catches variations like "Password123!", "Welcome123!", etc.
        for (String commonPassword : COMMON_PASSWORDS) {
            if (lowerPassword.startsWith(commonPassword)) {
                return false;
            }
        }

        return true;
    }
}
