package com.prayer_chat.chatbot;

import com.prayer_chat.chatbot.config.EnvironmentVariableConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the AI Chatbot System
 * 
 * This application provides:
 * - Website content analysis and crawling
 * - AI-powered chatbot generation
 * - Multi-language support
 * - Custom branding and styling
 * - Conversation tracking and analytics
 * 
 * Note: spring-dotenv 4.0.0 auto-loads .env files from the working directory
 * 
 * EnvironmentVariableConfig ensures environment variables are available as Spring properties
 * before Spring AI auto-configuration runs.
 * 
 * EnableJpaRepositories with entityManagerFactoryRef ensures repositories use the primary
 * EntityManagerFactory and avoids "Cannot resolve reference to bean 'jpaSharedEM_entityManagerFactory'"
 * when Spring Session JDBC and JPA are both active (e.g. on Render).
 * 
 * @author Prayer-Chat Development Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableJpaRepositories(entityManagerFactoryRef = "entityManagerFactory", basePackages = "com.prayer_chat.chatbot.repository")
@EnableRetry  // Enable Spring Retry for Spring AI retry functionality
@EnableAsync
@EnableScheduling
public class AiChatbotApplication {

    public static void main(String[] args) {
        System.out.println("🚀 Starting Prayer-Chat AI Chatbot System...");
        // Set Spring AI properties from env before any config runs, so auto-config can create ChatModel/EmbeddingModel
        String anthropicKey = System.getenv("ANTHROPIC_API_KEY");
        if (anthropicKey != null && !anthropicKey.isBlank()) {
            System.setProperty("spring.ai.anthropic.api-key", anthropicKey);
        }
        String cohereKey = System.getenv("COHERE_API_KEY");
        if (cohereKey != null && !cohereKey.isBlank()) {
            System.setProperty("spring.ai.cohere.api-key", cohereKey);
        }
        System.out.println("🔧 Registering EnvironmentVariableConfig listener...");
        
        SpringApplication app = new SpringApplication(AiChatbotApplication.class);
        
        // Register EnvironmentVariableConfig listener (Spring Boot 4.0.0 compatible)
        EnvironmentVariableConfig config = new EnvironmentVariableConfig();
        app.addListeners(config);
        System.out.println("✅ EnvironmentVariableConfig listener registered");
        
        System.out.println("🔧 Starting Spring Boot application...");
        app.run(args);
        System.out.println("\n🚀 Prayer-Chat AI Chatbot System is running!");
        System.out.println("📊 Frontend Dashboard: http://localhost:3000");
        System.out.println("🔧 Backend API: http://localhost:8081");
        System.out.println("💾 H2 Console: http://localhost:8081/h2-console\n");
    }
}
