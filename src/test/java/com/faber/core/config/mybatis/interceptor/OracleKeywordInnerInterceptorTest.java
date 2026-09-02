package com.faber.core.config.mybatis.interceptor;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OracleKeywordInnerInterceptorTest {

    @Test
    void shouldQuoteOnlyUnquotedLevelIdentifiers() {
        assertEquals("SELECT \"LEVEL\", t.\"LEVEL\", 'level' FROM menu t WHERE \"LEVEL\" = 1",
                OracleKeywordInnerInterceptor.quoteReservedLevel(
                        "SELECT level, t.level, 'level' FROM menu t WHERE \"LEVEL\" = 1"));
    }

    @Test
    void shouldUpdateBoundSqlBeforeExecution() {
        BoundSql boundSql = new BoundSql(new Configuration(), "SELECT level FROM base_rbac_menu", List.of(), null);
        StatementHandler statementHandler = mock(StatementHandler.class);
        when(statementHandler.getBoundSql()).thenReturn(boundSql);

        new OracleKeywordInnerInterceptor().beforePrepare(statementHandler, null, null);

        assertEquals("SELECT \"LEVEL\" FROM base_rbac_menu", boundSql.getSql());
    }
}
