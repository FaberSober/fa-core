package com.faber.core.config.mybatis.interceptor;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * 达梦数据库SQL拦截器
 * 用于处理字段名和表名的双引号格式化
 */
@Component
@Intercepts({
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
    @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class DamengSqlInterceptor implements Interceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(DamengSqlInterceptor.class);
    
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];

        logger.info("拦截器被调用 - MappedStatement ID: {}", mappedStatement.getId());

        // 强制检查达梦数据库（先简化逻辑）
        boolean isDameng = true; // 暂时强制为true来测试
        logger.info("强制设置为达梦数据库: {}", isDameng);

        if (isDameng) {
            BoundSql boundSql = mappedStatement.getBoundSql(parameter);
            String originalSql = boundSql.getSql();
            logger.info("拦截器 - 原始SQL: {}", originalSql);

            String processedSql = processSqlForDameng(originalSql);
            logger.info("拦截器 - 处理后SQL: {}", processedSql);

            if (!originalSql.equals(processedSql)) {
                // 使用反射修改BoundSql中的SQL
                try {
                    java.lang.reflect.Field sqlField = BoundSql.class.getDeclaredField("sql");
                    sqlField.setAccessible(true);
                    sqlField.set(boundSql, processedSql);
                    logger.info("拦截器 - SQL修改成功");
                } catch (Exception e) {
                    logger.error("拦截器 - 修改SQL失败: {}", e.getMessage(), e);
                }
            } else {
                logger.info("拦截器 - SQL无需修改");
            }
        }

        return invocation.proceed();
    }
    
    /**
     * 检查是否为达梦数据库
     */
    private boolean isDamengDatabase(MappedStatement mappedStatement) {
        try {
            DataSource dataSource = mappedStatement.getConfiguration().getEnvironment().getDataSource();
            Connection conn = dataSource.getConnection();
            String databaseProductName = conn.getMetaData().getDatabaseProductName();
            logger.info("检测到数据库类型: {}", databaseProductName);
            conn.close();
            boolean isDameng = "DM DBMS".equalsIgnoreCase(databaseProductName) ||
                              databaseProductName.toLowerCase().contains("dm");
            logger.info("是否为达梦数据库: {}", isDameng);
            return isDameng;
        } catch (Exception e) {
            logger.error("检测数据库类型失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 处理达梦数据库SQL，为字段名添加双引号
     */
    private String processSqlForDameng(String sql) {
        logger.info("开始处理SQL: {}", sql);

        // 简单粗暴的方式：直接替换常见的字段名
        String processedSql = sql;

        // 处理INSERT语句中的字段名 - 使用更简单的方式
        if (sql.toUpperCase().contains("INSERT INTO")) {
            // 替换常见的未加引号的字段名
            processedSql = processedSql.replaceAll("\\b(url)\\b", "\"$1\"");
            processedSql = processedSql.replaceAll("\\b(method)\\b", "\"$1\"");
            processedSql = processedSql.replaceAll("\\b(duration)\\b", "\"$1\"");
            processedSql = processedSql.replaceAll("\\b(agent)\\b", "\"$1\"");
            processedSql = processedSql.replaceAll("\\b(os)\\b", "\"$1\"");
            processedSql = processedSql.replaceAll("\\b(browser)\\b", "\"$1\"");
            processedSql = processedSql.replaceAll("\\b(version)\\b", "\"$1\"");
            processedSql = processedSql.replaceAll("\\b(mobile)\\b", "\"$1\"");
            processedSql = processedSql.replaceAll("\\b(biz)\\b", "\"$1\"");
            processedSql = processedSql.replaceAll("\\b(opr)\\b", "\"$1\"");
            processedSql = processedSql.replaceAll("\\b(crud)\\b", "\"$1\"");

            logger.info("字段名替换后的SQL: {}", processedSql);
        }

        return processedSql;
    }
    
    /**
     * 处理字段名，为未加引号的字段添加双引号
     */
    private String processColumns(String columns) {
        String[] columnArray = columns.split(",");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < columnArray.length; i++) {
            String column = columnArray[i].trim();

            // 如果字段名没有被双引号包围，则添加双引号
            if (!column.startsWith("\"") && !column.endsWith("\"")) {
                column = "\"" + column + "\"";
            }

            result.append(column);
            if (i < columnArray.length - 1) {
                result.append(", ");
            }
        }

        return result.toString();
    }

    /**
     * 处理UPDATE语句的SET子句
     */
    private String processSetClause(String setClause) {
        String[] assignments = setClause.split(",");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < assignments.length; i++) {
            String assignment = assignments[i].trim();

            // 查找等号位置
            int equalIndex = assignment.indexOf("=");
            if (equalIndex > 0) {
                String columnName = assignment.substring(0, equalIndex).trim();
                String value = assignment.substring(equalIndex + 1).trim();

                // 如果字段名没有被双引号包围，则添加双引号
                if (!columnName.startsWith("\"") && !columnName.endsWith("\"")) {
                    columnName = "\"" + columnName + "\"";
                }

                result.append(columnName).append(" = ").append(value);
            } else {
                result.append(assignment);
            }

            if (i < assignments.length - 1) {
                result.append(", ");
            }
        }

        return result.toString();
    }

    /**
     * 处理SELECT语句
     */
    private String processSelectStatement(String sql) {
        // 简单处理：替换SELECT和WHERE子句中的字段名
        // 这里可以根据需要扩展
        return sql;
    }

    /**
     * 处理UPDATE语句
     */
    private String processUpdateStatement(String sql) {
        Pattern updatePattern = Pattern.compile("\\bUPDATE\\s+([^\\s]+)\\s+SET\\s+([^\\s]+(?:\\s*=\\s*[^,]+(?:,\\s*[^\\s]+\\s*=\\s*[^,]+)*)?)", Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher updateMatcher = updatePattern.matcher(sql);

        if (updateMatcher.find()) {
            String tableName = updateMatcher.group(1);
            String setClause = updateMatcher.group(2);

            // 处理SET子句中的字段名
            String processedSetClause = processSetClause(setClause);

            sql = sql.replace(updateMatcher.group(0), "UPDATE " + tableName + " SET " + processedSetClause);
        }

        return sql;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 可以在这里设置属性
    }
}
