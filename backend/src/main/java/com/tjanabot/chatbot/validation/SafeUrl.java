package com.tjanabot.chatbot.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Custom validation annotation for safe URLs
 * Prevents SSRF attacks by validating URLs don't point to internal resources
 */
@Documented
@Constraint(validatedBy = SafeUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeUrl {
    String message() default "URL is not safe or points to internal resources";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
