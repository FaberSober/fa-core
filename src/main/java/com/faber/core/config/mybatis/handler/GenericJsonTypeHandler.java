package com.faber.core.config.mybatis.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.*;

/**
 * 通用 JSON TypeHandler，支持 PostgreSQL(jsonb) / MySQL(json)
 * 可以处理任意类型，如 Map、List、POJO 等
 */
public class GenericJsonTypeHandler<T> extends BaseTypeHandler<T> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TypeReference<T> typeReference;

    /** 无参构造器，用于 MyBatis-Plus 自动实例化 */
    public GenericJsonTypeHandler() {
        // 默认 Object 类型，避免报错
        this.typeReference = new TypeReference<>() {};
    }
    
    public GenericJsonTypeHandler(TypeReference<T> typeReference) {
        this.typeReference = typeReference;
    }

    /**
     * 提供静态工厂方法，方便在 @TableField 中使用
     */
    public static <T> GenericJsonTypeHandler<T> forType(TypeReference<T> typeReference) {
        return new GenericJsonTypeHandler<>(typeReference);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        try {
            String json = MAPPER.writeValueAsString(parameter);
            String dbProduct = ps.getConnection().getMetaData().getDatabaseProductName().toLowerCase();

            if (dbProduct.contains("postgresql")) {
                PGobject pgObject = new PGobject();
                pgObject.setType("jsonb");
                pgObject.setValue(json);
                ps.setObject(i, pgObject);
            } else {
                ps.setString(i, json);
            }
        } catch (Exception e) {
            throw new SQLException("Failed to set JSON parameter", e);
        }
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return parseJson(value);
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return parseJson(value);
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return parseJson(value);
    }

    private T parseJson(String value) throws SQLException {
        if (value == null) return null;
        try {
            return MAPPER.readValue(value, typeReference);
        } catch (Exception e) {
            throw new SQLException("Failed to parse JSON to object", e);
        }
    }
}



