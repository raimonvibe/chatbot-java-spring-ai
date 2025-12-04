package com.tjanabot.chatbot.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Custom validation annotation to reject common passwords
 */
@Documented
@Constraint(validatedBy = NotCommonPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotCommonPassword {
    String message() default "Password is too common and easily guessable";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
