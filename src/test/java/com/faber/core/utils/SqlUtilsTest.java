package com.faber.core.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlUtilsTest {

    @Test
    void shouldKeepPostgreSqlDollarQuotedFunctionAsOneStatement() {
        String sql = """
                CREATE OR REPLACE FUNCTION test_function()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    NEW.updated_at = CURRENT_TIMESTAMP;
                    RETURN NEW;
                END;
                $$;
                CREATE TABLE test_table (id bigint);
                """;

        List<String> statements = SqlUtils.splitSqlStatements(sql);

        assertEquals(2, statements.size());
        assertEquals(true, statements.get(0).contains("RETURN NEW;"));
        assertEquals("CREATE TABLE test_table (id bigint)", statements.get(1));
    }

    @Test
    void shouldKeepSemicolonsInsideQuotedValues() {
        List<String> statements = SqlUtils.splitSqlStatements(
                "INSERT INTO test_table VALUES ('a;b'); SELECT \"a;b\" FROM test_table;"
        );

        assertEquals(2, statements.size());
    }

    @Test
    void shouldRejectDestructiveTableAndSchemaDdl() {
        assertDestructiveDdlRejected("-- comment\nDROP TABLE IF EXISTS base_job");
        assertDestructiveDdlRejected("DROP /* comment */ SCHEMA IF EXISTS public CASCADE");
        assertDestructiveDdlRejected("TRUNCATE TABLE base_job");
        assertDestructiveDdlRejected("TRUNCATE base_job");
    }

    @Test
    void shouldAllowNonDestructiveDdlAndDataFixes() {
        assertDoesNotThrow(() -> SqlUtils.validateUpgradeSqlSafety("CREATE TABLE IF NOT EXISTS base_job (id bigint)"));
        assertDoesNotThrow(() -> SqlUtils.validateUpgradeSqlSafety("ALTER TABLE base_job DROP COLUMN obsolete_column"));
        assertDoesNotThrow(() -> SqlUtils.validateUpgradeSqlSafety("DELETE FROM base_system_update_log WHERE id = 1"));
    }

    private void assertDestructiveDdlRejected(String sql) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SqlUtils.validateUpgradeSqlSafety(sql)
        );
        assertEquals(true, exception.getMessage().contains("禁止执行破坏性DDL"));
    }
}
