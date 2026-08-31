package com.faber.core.utils;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL utils
 * @author xu.pengfei
 * @date 2022/11/28 14:33
 */
public class SqlUtils {

    /**
     * LIKE查询替换特殊字符
     * @param value
     * @return
     */
    public static String filterLikeValue(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /**
     * 执行sql脚本
     *
     * @param sql
     * @throws SQLException
     */
    public static void executeSql(Connection conn, String sql) throws SQLException {
        conn.setAutoCommit(true);
        try (Statement statement = conn.createStatement()) {
            for (String sqlStatement : splitSqlStatements(sql)) {
                validateUpgradeSqlSafety(sqlStatement);
                statement.execute(sqlStatement);
            }
        }
    }

    static List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String dollarQuote = null;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int index = 0; index < sql.length(); index++) {
            char currentChar = sql.charAt(index);
            char nextChar = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';

            if (lineComment) {
                current.append(currentChar);
                if (currentChar == '\n' || currentChar == '\r') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                current.append(currentChar);
                if (currentChar == '*' && nextChar == '/') {
                    current.append(nextChar);
                    index++;
                    blockComment = false;
                }
                continue;
            }
            if (dollarQuote != null) {
                if (sql.startsWith(dollarQuote, index)) {
                    current.append(dollarQuote);
                    index += dollarQuote.length() - 1;
                    dollarQuote = null;
                } else {
                    current.append(currentChar);
                }
                continue;
            }
            if (singleQuoted) {
                current.append(currentChar);
                if (currentChar == '\\' && index + 1 < sql.length()) {
                    current.append(nextChar);
                    index++;
                } else if (currentChar == '\'' && nextChar == '\'') {
                    current.append(nextChar);
                    index++;
                } else if (currentChar == '\'') {
                    singleQuoted = false;
                }
                continue;
            }
            if (doubleQuoted) {
                current.append(currentChar);
                if (currentChar == '"' && nextChar == '"') {
                    current.append(nextChar);
                    index++;
                } else if (currentChar == '"') {
                    doubleQuoted = false;
                }
                continue;
            }

            if (currentChar == '-' && nextChar == '-') {
                current.append(currentChar).append(nextChar);
                index++;
                lineComment = true;
            } else if (currentChar == '/' && nextChar == '*') {
                current.append(currentChar).append(nextChar);
                index++;
                blockComment = true;
            } else if (currentChar == '\'') {
                current.append(currentChar);
                singleQuoted = true;
            } else if (currentChar == '"') {
                current.append(currentChar);
                doubleQuoted = true;
            } else if (currentChar == '$') {
                String quote = findDollarQuote(sql, index);
                if (quote == null) {
                    current.append(currentChar);
                } else {
                    current.append(quote);
                    index += quote.length() - 1;
                    dollarQuote = quote;
                }
            } else if (currentChar == ';') {
                addStatement(statements, current);
            } else {
                current.append(currentChar);
            }
        }
        addStatement(statements, current);
        return statements;
    }

    static void validateUpgradeSqlSafety(String sqlStatement) {
        List<String> keywords = extractLeadingKeywords(sqlStatement, 4);
        if (keywords.isEmpty()) {
            return;
        }
        if ("TRUNCATE".equals(keywords.get(0))) {
            throw forbiddenDestructiveDdl("TRUNCATE");
        }
        if (!"DROP".equals(keywords.get(0))) {
            return;
        }

        int objectTypeIndex = 1;
        if (keywords.size() >= 3 && "IF".equals(keywords.get(1)) && "EXISTS".equals(keywords.get(2))) {
            objectTypeIndex = 3;
        }
        if (keywords.size() > objectTypeIndex
                && ("TABLE".equals(keywords.get(objectTypeIndex))
                || "SCHEMA".equals(keywords.get(objectTypeIndex)))) {
            throw forbiddenDestructiveDdl("DROP " + keywords.get(objectTypeIndex));
        }
    }

    private static IllegalArgumentException forbiddenDestructiveDdl(String operation) {
        return new IllegalArgumentException("升级SQL禁止执行破坏性DDL：" + operation);
    }

    private static List<String> extractLeadingKeywords(String sql, int maxKeywords) {
        List<String> keywords = new ArrayList<>(maxKeywords);
        String dollarQuote = null;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int index = 0; index < sql.length() && keywords.size() < maxKeywords; index++) {
            char currentChar = sql.charAt(index);
            char nextChar = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';

            if (lineComment) {
                if (currentChar == '\n' || currentChar == '\r') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                if (currentChar == '*' && nextChar == '/') {
                    index++;
                    blockComment = false;
                }
                continue;
            }
            if (dollarQuote != null) {
                if (sql.startsWith(dollarQuote, index)) {
                    index += dollarQuote.length() - 1;
                    dollarQuote = null;
                }
                continue;
            }
            if (singleQuoted) {
                if (currentChar == '\\' && index + 1 < sql.length()) {
                    index++;
                } else if (currentChar == '\'' && nextChar == '\'') {
                    index++;
                } else if (currentChar == '\'') {
                    singleQuoted = false;
                }
                continue;
            }
            if (doubleQuoted) {
                if (currentChar == '"' && nextChar == '"') {
                    index++;
                } else if (currentChar == '"') {
                    doubleQuoted = false;
                }
                continue;
            }

            if (currentChar == '-' && nextChar == '-') {
                index++;
                lineComment = true;
            } else if (currentChar == '/' && nextChar == '*') {
                index++;
                blockComment = true;
            } else if (currentChar == '\'') {
                singleQuoted = true;
            } else if (currentChar == '"') {
                doubleQuoted = true;
            } else if (currentChar == '$') {
                String quote = findDollarQuote(sql, index);
                if (quote != null) {
                    index += quote.length() - 1;
                    dollarQuote = quote;
                }
            } else if (Character.isLetter(currentChar)) {
                int keywordEnd = index + 1;
                while (keywordEnd < sql.length()) {
                    char keywordChar = sql.charAt(keywordEnd);
                    if (!(Character.isLetterOrDigit(keywordChar) || keywordChar == '_')) {
                        break;
                    }
                    keywordEnd++;
                }
                keywords.add(sql.substring(index, keywordEnd).toUpperCase());
                index = keywordEnd - 1;
            }
        }
        return keywords;
    }

    private static String findDollarQuote(String sql, int startIndex) {
        int endIndex = sql.indexOf('$', startIndex + 1);
        if (endIndex < 0) {
            return null;
        }
        for (int index = startIndex + 1; index < endIndex; index++) {
            char quoteChar = sql.charAt(index);
            if (!(Character.isLetterOrDigit(quoteChar) || quoteChar == '_')) {
                return null;
            }
        }
        return sql.substring(startIndex, endIndex + 1);
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
        current.setLength(0);
    }

}
