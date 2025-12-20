package com.tjanabot.chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class LoggingConfig {

    @Value("${logging.file.name:logs/chatbot.log}")
    private String logFileName;

    @PostConstruct
    public void init() {
        try {
            Path logPath = Paths.get(logFileName);
            Path parentDir = logPath.getParent();

            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                System.out.println("Created log directory: " + parentDir.toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Failed to create log directory: " + e.getMessage());
        }
    }
}
