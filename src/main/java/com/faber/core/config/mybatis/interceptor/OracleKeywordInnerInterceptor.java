package com.faber.core.config.mybatis.interceptor;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.executor.statement.StatementHandler;

import java.sql.Connection;
import java.util.regex.Pattern;

/** Oracle 保留字兼容。 */
public class OracleKeywordInnerInterceptor implements InnerInterceptor {

    private static final Pattern LEVEL = Pattern.compile("(?i)(?<![\\w\"'])\\blevel\\b(?![\\w\"'])");
    private static final Pattern SIZE = Pattern.compile("(?i)(?<![\\w\"'])\\bsize\\b(?![\\w\"'])");

    @Override
    public void beforePrepare(StatementHandler statementHandler, Connection connection, Integer transactionTimeout) {
        BoundSql boundSql = statementHandler.getBoundSql();
        SystemMetaObject.forObject(boundSql).setValue("sql", quoteReservedIdentifiers(boundSql.getSql()));
    }

    static String quoteReservedIdentifiers(String sql) {
        return SIZE.matcher(LEVEL.matcher(sql).replaceAll("\"LEVEL\"")).replaceAll("\"size\"");
    }
}
