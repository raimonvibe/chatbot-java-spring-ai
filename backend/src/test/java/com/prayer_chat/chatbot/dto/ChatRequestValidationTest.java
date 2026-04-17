package com.prayer_chat.chatbot.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    void messageAcceptsEmojiAndLetters() {
        ChatRequest r = new ChatRequest("Good night \uD83C\uDF19", "session_1", "en");
        assertTrue(validator.validate(r).isEmpty(), "crescent moon emoji should be allowed");
    }

    @Test
    void messageAcceptsMixedScriptAndWhitespace() {
        ChatRequest r = new ChatRequest("Hello 你好 café", "s", "en");
        assertTrue(validator.validate(r).isEmpty());
    }

    @Test
    void messageRejectsNullBytes() {
        ChatRequest r = new ChatRequest("bad\u0000char", "s", "en");
        assertFalse(validator.validate(r).isEmpty());
    }
}
