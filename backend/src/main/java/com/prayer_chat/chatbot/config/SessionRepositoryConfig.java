package com.prayer_chat.chatbot.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

/**
 * Configures Spring Session JDBC so that on PostgreSQL, saving session
 * attributes uses an upsert (INSERT ... ON CONFLICT DO UPDATE) instead of
 * plain INSERT.
 * <p>
 * This avoids {@code duplicate key value violates unique constraint
 * "spring_session_attributes_pk"} when the same session attribute (e.g.
 * SPRING_SECURITY_CONTEXT) is written concurrently—e.g. multiple requests
 * in the same session or request forwarding that triggers a second commit.
 * On non-PostgreSQL databases (e.g. H2 in tests) the default INSERT is
 * left unchanged because H2 does not support ON CONFLICT. The placeholder
 * {@code %TABLE_NAME%} is replaced by the configured session table name
 * (e.g. SPRING_SESSION).
 */
@Configuration
public class SessionRepositoryConfig {

    /**
     * PostgreSQL-only upsert for session attributes so concurrent writes for
     * the same (session_primary_id, attribute_name) update the row instead of
     * causing a duplicate key error.
     */
    private static final String CREATE_SESSION_ATTRIBUTE_UPSERT = ""
            + "INSERT INTO %TABLE_NAME%_ATTRIBUTES (SESSION_PRIMARY_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES) "
            + "VALUES (?, ?, ?) "
            + "ON CONFLICT (SESSION_PRIMARY_ID, ATTRIBUTE_NAME) "
            + "DO UPDATE SET ATTRIBUTE_BYTES = EXCLUDED.ATTRIBUTE_BYTES";

    @Bean
    public SessionRepositoryCustomizer<JdbcIndexedSessionRepository> sessionRepositoryCustomizer(DataSource dataSource) {
        return sessionRepository -> {
            if (isPostgreSQL(dataSource)) {
                sessionRepository.setCreateSessionAttributeQuery(CREATE_SESSION_ATTRIBUTE_UPSERT);
            }
        };
    }

    private static boolean isPostgreSQL(DataSource dataSource) {
        try (var conn = dataSource.getConnection()) {
            return conn.getMetaData().getURL().toLowerCase().contains("postgresql");
        } catch (Exception e) {
            return false;
        }
    }
}
