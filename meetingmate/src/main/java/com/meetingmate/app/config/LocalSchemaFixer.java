package com.meetingmate.app.config;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
@Profile("local")
@Slf4j
public class LocalSchemaFixer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void patchUserTable() {
        // Local compatibility patch for legacy schemas that predate providerId/createdAt.
        if (!tableExists("user")) {
            log.debug("Skipping local schema patch because user table does not exist yet.");
            return;
        }

        ensureCreatedAtColumn();
        ensureProviderIdColumn();
    }

    private void ensureCreatedAtColumn() {
        if (!columnExists("user", "created_at")) {
            jdbcTemplate.execute("""
                    ALTER TABLE `user`
                    ADD COLUMN created_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6)
                    """);
        }

        jdbcTemplate.update("""
                UPDATE `user`
                SET created_at = CURRENT_TIMESTAMP(6)
                WHERE created_at IS NULL
                """);

        jdbcTemplate.execute("""
                ALTER TABLE `user`
                MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                """);
    }

    private void ensureProviderIdColumn() {
        if (columnExists("user", "provider_id")) {
            return;
        }

        jdbcTemplate.execute("""
                ALTER TABLE `user`
                ADD COLUMN provider_id VARCHAR(255) NULL
                """);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }
}

@Component
@Profile("local")
class LocalSchemaFixerEntityManagerFactoryDependency
        extends AbstractDependsOnBeanFactoryPostProcessor {

    LocalSchemaFixerEntityManagerFactoryDependency() {
        super(EntityManagerFactory.class, "localSchemaFixer");
    }
}
