package com.prayer_chat.chatbot.config;

import java.util.Arrays;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Registers an alias so that the primary JPA EntityManagerFactory bean
 * ("entityManagerFactory", created by Spring Boot auto-configuration) is also
 * visible under the name "jpaSharedEM_entityManagerFactory".
 *
 * This avoids startup failures in tests or environments where repository
 * configuration still tries to resolve "jpaSharedEM_entityManagerFactory",
 * without creating a second EntityManagerFactory bean or changing security
 * behavior.
 */
@Configuration
public class JpaEntityManagerFactoryConfig implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!beanFactory.containsBeanDefinition("entityManagerFactory")) {
            return;
        }

        // If an explicit jpaSharedEM_entityManagerFactory bean/alias already exists, don't override it.
        if (beanFactory.containsBeanDefinition("jpaSharedEM_entityManagerFactory")) {
            return;
        }

        String[] aliases = beanFactory.getAliases("entityManagerFactory");
        boolean alreadyAliased = Arrays.asList(aliases).contains("jpaSharedEM_entityManagerFactory");
        if (!alreadyAliased) {
            beanFactory.registerAlias("entityManagerFactory", "jpaSharedEM_entityManagerFactory");
        }
    }
}
