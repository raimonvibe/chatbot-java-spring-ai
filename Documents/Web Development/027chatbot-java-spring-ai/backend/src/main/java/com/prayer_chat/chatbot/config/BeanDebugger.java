package com.prayer_chat.chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Bean debugger to diagnose Spring bean creation issues
 * Logs all AI-related beans after application context is fully initialized
 */
@Component
@Profile("!test")
public class BeanDebugger implements ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(BeanDebugger.class);

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        logger.info("🔍 ========================================");
        logger.info("🔍 BEAN DEBUGGER START");
        logger.info("🔍 ========================================");

        // Check if EmbeddingModel bean exists
        try {
            EmbeddingModel em = context.getBean(EmbeddingModel.class);
            logger.info("✅ EmbeddingModel bean found: {}", em.getClass().getName());
        } catch (NoSuchBeanDefinitionException e) {
            logger.error("❌ EmbeddingModel bean NOT FOUND in Spring context");
            logger.error("❌ This means embeddingModel() @Bean method was never called or failed");
        } catch (Exception e) {
            logger.error("❌ Error retrieving EmbeddingModel bean: {}", e.getMessage(), e);
        }

        // Check if VectorStore bean exists
        try {
            VectorStore vs = context.getBean(VectorStore.class);
            logger.info("✅ VectorStore bean found: {}", vs.getClass().getName());
        } catch (NoSuchBeanDefinitionException e) {
            logger.error("❌ VectorStore bean NOT FOUND in Spring context");
            logger.error("❌ This means vectorStore() @Bean method was never called or failed");
        } catch (Exception e) {
            logger.error("❌ Error retrieving VectorStore bean: {}", e.getMessage(), e);
        }

        // List all AI-related beans
        String[] beanNames = context.getBeanDefinitionNames();
        logger.info("🔍 Searching {} total beans for AI-related beans...", beanNames.length);

        int foundCount = 0;
        for (String name : beanNames) {
            String lowerName = name.toLowerCase();
            if (lowerName.contains("vector") ||
                lowerName.contains("embedding") ||
                lowerName.contains("cohere") ||
                lowerName.contains("anthropic") ||
                lowerName.contains("chat")) {

                try {
                    Object bean = context.getBean(name);
                    logger.info("🔍 Found bean: {} -> {}", name, bean.getClass().getName());
                    foundCount++;
                } catch (Exception e) {
                    logger.warn("🔍 Found bean name '{}' but failed to retrieve: {}", name, e.getMessage());
                }
            }
        }

        logger.info("🔍 Found {} AI-related beans", foundCount);
        logger.info("🔍 ========================================");
        logger.info("🔍 BEAN DEBUGGER END");
        logger.info("🔍 ========================================");
    }
}
