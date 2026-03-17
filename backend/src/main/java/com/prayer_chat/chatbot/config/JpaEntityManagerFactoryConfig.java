package com.prayer_chat.chatbot.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the primary EntityManagerFactory under the bean name
 * "jpaSharedEM_entityManagerFactory" so that Spring Data JPA repository beans
 * that resolve this name (e.g. when Spring Session JDBC and JPA are both active
 * on Render) can find the EMF and the application starts successfully.
 */
@Configuration
public class JpaEntityManagerFactoryConfig {

    @Bean(name = "jpaSharedEM_entityManagerFactory")
    public EntityManagerFactory jpaSharedEMEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        return entityManagerFactory;
    }
}
