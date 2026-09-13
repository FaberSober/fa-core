package com.faber.core.config.mybatis.utils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.faber.core.annotation.SqlEquals;
import com.faber.core.annotation.SqlSearch;
import com.faber.core.utils.FaMapUtils;
import com.faber.core.utils.SqlUtils;
import com.faber.core.vo.query.Condition;
import com.faber.core.vo.query.ConditionGroup;
import com.faber.core.vo.query.QueryParams;
import com.faber.core.vo.query.Sorter;
import com.faber.core.vo.query.enums.ConditionGroupTypeEnum;

import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于MybatisPlus的高级组合查询帮助类
 * @author xu.pengfei
 * @date 2022/11/28 14:22
 */
@Slf4j
public class WrapperUtils {

    public static <T> QueryWrapper<T> parseQuery(QueryParams queryParams, Class<T> clazz) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();

        Map<String, Object> query = FaMapUtils.removeEmptyValue(queryParams.getQuery());

        boolean condition = query.size() > 0;
        wrapper.and(condition, ew -> {
            for (Map.Entry<String, Object> entry : query.entrySet()) {
                // xxx#$min，xxx#$max 类型的key，为最小值、最大值判定
                String key = entry.getKey();


                if (key.contains("#$")) {
                    String fieldName = key.substring(0, key.indexOf("#$"));
                    fieldName = StrUtil.toUnderlineCase(fieldName); // 转下划线
                    String opr = key.substring(key.indexOf("#$") + 2);

                    Field javaField = ReflectUtil.getField(clazz, key.substring(0, key.indexOf("#$")));
                    Object realValue = parseValueIfNeed(javaField, entry.getValue());

                    switch (opr) {
                        case "min":
                            ew.ge(fieldName, realValue);
                            break;
                        case "max":
                            ew.le(fieldName, realValue);
                            break;
                        case "likeLeft":
                            if (isNumberField(javaField)) {
                                ew.eq(fieldName, realValue);
                            } else {
                                ew.likeLeft(fieldName, entry.getValue());
                            }
                            break;
                        case "likeRight":
                            if (isNumberField(javaField)) {
                                ew.eq(fieldName, realValue);
                            } else {
                                ew.likeRight(fieldName, entry.getValue());
                            }
                            break;
                        case "in":
                            if (entry.getValue() != null && StringUtils.isNotEmpty(entry.getValue().toString())) {
                                List<Object> list = valueToList(entry.getValue(), javaField);
                                ew.in(list.size() > 0, fieldName, list);
                            }
                            break;
                        case "notIn":
                            if (entry.getValue() != null && StringUtils.isNotEmpty(entry.getValue().toString())) {
                                List<Object> list = valueToList(entry.getValue(), javaField);
                                ew.notIn(list.size() > 0, fieldName, list);
                            }
                            break;
                        case "gt":
                            ew.gt(fieldName, realValue);
                            break;
                        case "lt":
                            ew.lt(fieldName, realValue);
                            break;
                    }

                    continue;
                }

                if (StrUtil.isEmpty(entry.getValue().toString())) continue;

                // TO-DO: 增加注解方式，有的string属性需要强制指定为equals查询
                Field field = ReflectUtil.getField(clazz, entry.getKey());
                boolean forceEqual = judgeFieldEqual(field);

                Object realValue = parseValueIfNeed(field, entry.getValue());

//                if (field == null) {
//                    log.warn("No field {} Found", entry.getKey());
//                    continue;
//                }

                String fieldColumn = StrUtil.toUnderlineCase(entry.getKey());
                if (forceEqual) {
                    ew.eq(fieldColumn, realValue);
                } else {
                    if (isNumberField(field) || realValue instanceof Boolean) {
                        ew.eq(fieldColumn, realValue);
                    } else if (realValue instanceof Date 
                            || realValue instanceof LocalDateTime 
                            || realValue instanceof LocalDate 
                            || realValue instanceof LocalTime) {
                        // 时间字段不能用 like，必须 eq
                        ew.eq(fieldColumn, realValue);
                    } else {
                        ew.like(fieldColumn, SqlUtils.filterLikeValue(StrUtil.toString(realValue)));
                    }
                }
            }
        });

        // 单查询字段
        if (StringUtils.isNotEmpty(queryParams.getSearch())) {
            wrapper.and(ew -> {
                for (Field field : clazz.getDeclaredFields()) {
                    SqlSearch annotation = field.getAnnotation(SqlSearch.class);
                    if (annotation != null) {
                        String fieldColumn = StrUtil.toUnderlineCase(field.getName());
                        ew.or().like(fieldColumn, SqlUtils.filterLikeValue(queryParams.getSearch()));
                    }
                }
            });
        }

        // 高级查询-过滤条件List
        if (queryParams.getConditionList() != null && queryParams.getConditionList().size() > 0) {
            for (ConditionGroup conditionGroup : queryParams.getConditionList()) {
                processConditionList(conditionGroup, wrapper, clazz);
            }
        }

        List<Sorter> sorterList = queryParams.getSorterInfo();
        if (sorterList != null && !sorterList.isEmpty()) {
            for (Sorter sorter : sorterList) {
                wrapper.orderBy(true, sorter.isAsc(), sorter.getField());
            }
        }

        return wrapper;
    }

    /**
     * 判断field是否是等于=查询
     * @param field
     * @return
     */
    private static boolean judgeFieldEqual(Field field) {
        if (field == null) return false;

        // 如果是id
        if ("id".equalsIgnoreCase(field.getName())) return true;

        // SqlEquals注解
        if (field.getAnnotation(SqlEquals.class) != null) return true;

        // Enum枚举类型
        if (ClassUtil.isEnum(field.getType())) return true;

        if (field.getType() == Date.class) return true;

        return false;
    }

    private static List<Object> valueToList(Object value, Field field) {
        List<Object> list = new ArrayList<>();
        if (value instanceof List<?> values) {
            for (Object item : values) {
                list.add(parseValueIfNeed(field, item));
            }
        } else {
            for (String item : Arrays.asList(ObjectUtil.toString(value).split("[,，]"))) {
                list.add(parseValueIfNeed(field, item));
            }
        }
        return list;
    }

    /**
     * @param conditionGroup
     * @param wrapper
     */
    private static <T> void processConditionList(ConditionGroup conditionGroup, QueryWrapper<T> wrapper, Class<T> clazz) {
        if (conditionGroup == null || conditionGroup.getCondList() == null) return;

        wrapper.and(conditionGroup.getCondList().size() > 0, ew -> {
            for (Condition cond : conditionGroup.getCondList()) {
                if (cond == null || cond.getOpr() == null) continue;

                String column = StrUtil.toUnderlineCase(cond.getKey());
                Field field = ReflectUtil.getField(clazz, cond.getKey());

                if (conditionGroup.getType() == ConditionGroupTypeEnum.OR) {
                    ew.or();
                }

                Object value = cond.getValue();
                Object realValue = parseValueIfNeed(field, value);
                switch (cond.getOpr()) {
                    case EQ:
                        ew.eq(column, realValue);
                        break;
                    case NE:
                        ew.ne(column, realValue);
                        break;
                    case IN: {
                        List<Object> list = valueToList(value, field);
                        ew.in(list.size() > 0, column, list);
                    } break;
                    case NOT_IN: {
                        List<Object> list = valueToList(value, field);
                        ew.notIn(list.size() > 0, column, list);
                    } break;
                    case LIKE:
                        if (isNumberField(field)) {
                            ew.eq(column, realValue);
                        } else {
                            ew.like(column, SqlUtils.filterLikeValue(ObjectUtil.toString(value)));
                        }
                        break;
                    case NOT_LIKE:
                        if (isNumberField(field)) {
                            ew.ne(column, realValue);
                        } else {
                            ew.notLike(column, SqlUtils.filterLikeValue(ObjectUtil.toString(value)));
                        }
                        break;
                    case LIKE_LEFT:
                        if (isNumberField(field)) {
                            ew.eq(column, realValue);
                        } else {
                            ew.likeLeft(column, SqlUtils.filterLikeValue(ObjectUtil.toString(value)));
                        }
                        break;
                    case LIKE_RIGHT:
                        if (isNumberField(field)) {
                            ew.eq(column, realValue);
                        } else {
                            ew.likeRight(column, SqlUtils.filterLikeValue(ObjectUtil.toString(value)));
                        }
                        break;
                    case GT:
                        ew.gt(column, realValue);
                        break;
                    case GE:
                        ew.ge(column, realValue);
                        break;
                    case LT:
                        ew.lt(column, realValue);
                        break;
                    case LE:
                        ew.le(column, realValue);
                        break;
                    case BETWEEN:
                        ew.between(column, parseValueIfNeed(field, cond.getBegin()), parseValueIfNeed(field, cond.getEnd()));
                        break;
                    case IS_NOT_NULL:
                        ew.isNotNull(column);
                        break;
                    case IS_NULL:
                        ew.isNull(column);
                        break;
                    default:
                        break;
                }
            }
        });
    }

    /**
     * 根据字段类型转换查询值，避免数据库字段类型与 JDBC 参数类型不一致
     */
    private static Object parseValueIfNeed(Field field, Object value) {
        if (field == null || value == null) return value;

        Class<?> type = field.getType();
        String str = value.toString().trim();

        if (isNumberType(type)) {
            if (type == Integer.class || type == int.class) return Integer.valueOf(str);
            if (type == Long.class || type == long.class) return Long.valueOf(str);
            if (type == Short.class || type == short.class) return Short.valueOf(str);
            if (type == Byte.class || type == byte.class) return Byte.valueOf(str);
            if (type == Double.class || type == double.class) return Double.valueOf(str);
            if (type == Float.class || type == float.class) return Float.valueOf(str);
            if (type == BigDecimal.class) return new BigDecimal(str);
            if (type == BigInteger.class) return new BigInteger(str);
        }

        // 支持的日期格式
        String[] patterns = new String[]{
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy/MM/dd",
                "yyyy-MM-dd HH:mm",
                "yyyy/MM/dd HH:mm"
        };

        // 日期类型处理 java.util.Date
        if (type == Date.class) {
            for (String p : patterns) {
                try {
                    return cn.hutool.core.date.DateUtil.parse(str, p).toJdkDate();
                } catch (Exception ignored) {}
            }
        }

        // LocalDateTime
        if (type == java.time.LocalDateTime.class) {
            for (String p : patterns) {
                try {
                    return cn.hutool.core.date.LocalDateTimeUtil.parse(str, p);
                } catch (Exception ignored) {}
            }
        }

        // LocalDate
        if (type == java.time.LocalDate.class) {
            for (String p : patterns) {
                try {
                    return cn.hutool.core.date.LocalDateTimeUtil.parse(str, p).toLocalDate();
                } catch (Exception ignored) {}
            }
        }

        // LocalTime
        if (type == java.time.LocalTime.class) {
            String[] timePatterns = new String[]{
                    "HH:mm:ss",
                    "HH:mm"
            };
            for (String p : timePatterns) {
                try {
                    return cn.hutool.core.date.LocalDateTimeUtil.parse(str, p).toLocalTime();
                } catch (Exception ignored) {}
            }
        }

        // 都解析失败，返回原值
        return value;
    }

    private static boolean isNumberField(Field field) {
        return field != null && isNumberType(field.getType());
    }

    private static boolean isNumberType(Class<?> type) {
        return type == Integer.class || type == int.class
                || type == Long.class || type == long.class
                || type == Short.class || type == short.class
                || type == Byte.class || type == byte.class
                || type == Double.class || type == double.class
                || type == Float.class || type == float.class
                || type == BigDecimal.class || type == BigInteger.class;
    }

}
