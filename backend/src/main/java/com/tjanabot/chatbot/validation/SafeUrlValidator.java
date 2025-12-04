package com.tjanabot.chatbot.validation;

import com.tjanabot.chatbot.service.UrlValidationService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Validator implementation for @SafeUrl annotation
 * Uses UrlValidationService to prevent SSRF attacks
 */
public class SafeUrlValidator implements ConstraintValidator<SafeUrl, String> {

    @Autowired
    private UrlValidationService urlValidationService;

    @Override
    public void initialize(SafeUrl constraintAnnotation) {
    }

    @Override
    public boolean isValid(String url, ConstraintValidatorContext context) {
        // Null values are handled by @NotBlank/@NotNull annotations
        if (url == null || url.trim().isEmpty()) {
            return true;
        }

        // Use UrlValidationService to check for SSRF attempts
        return urlValidationService.isValidAndSafe(url);
    }
}
