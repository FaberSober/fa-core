package com.faber.core.context;

import com.faber.core.constant.CommonConstants;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 上下文内容存储，当前线程本地存储
 */
public class TnTenantContextHandler {

    /** 当前线程本地存储，主要用于存储当前接口操作线程的用户信息 */
    public static ThreadLocal<Map<String, Object>> threadLocal = new ThreadLocal<Map<String, Object>>();

    public static void set(String key, Object value) {
        Map<String, Object> map = threadLocal.get();
        if (map == null) {
            map = new HashMap<String, Object>();
            threadLocal.set(map);
        }
        map.put(key, value);
    }

    public static Object get(String key) {
        Map<String, Object> map = threadLocal.get();
        if (map == null) {
            map = new HashMap<String, Object>();
            threadLocal.set(map);
        }
        return map.get(key);
    }

    public static void setLogin(Boolean login) {
        set(CommonConstants.CONTEXT_KEY_LOGIN, login);
    }

    public static Boolean getLogin() {
        Object value = get(CommonConstants.CONTEXT_KEY_LOGIN);
        return value != null && (Boolean) value;
    }

    public static void setUserId(Integer userId) {
        set(CommonConstants.CONTEXT_KEY_USER_ID, userId);
    }

    public static Integer getUserId() {
        return (Integer) get(CommonConstants.CONTEXT_KEY_USER_ID);
    }

    public static void setName(String name) {
        set(CommonConstants.CONTEXT_KEY_USER_NAME, name);
    }

    public static String getName() {
        return (String) get(CommonConstants.CONTEXT_KEY_USER_NAME);
    }

    public static void setIp(String ip) {
        set(CommonConstants.CONTEXT_KEY_USER_IP, ip);
    }

    public static String getIp() {
        return (String) get(CommonConstants.CONTEXT_KEY_USER_IP);
    }

    public static void setTenantId(Integer tenantId) {
        set(CommonConstants.CONTEXT_KEY_TENANT_ID, tenantId);
    }

    public static Integer getTenantId() {
        return (Integer) get(CommonConstants.CONTEXT_KEY_TENANT_ID);
    }

    public static void setCorpId(Integer corpId) {
        set(CommonConstants.CONTEXT_KEY_CORP_ID, corpId);
    }

    public static Integer getCorpId() {
        return (Integer) get(CommonConstants.CONTEXT_KEY_CORP_ID);
    }

    private static String returnObjectValue(Object value) {
        return value == null ? null : value.toString();
    }

    // ----------------------------------------- 线程缓存 -----------------------------------------
    public static <T> Map<Serializable, T> getCacheMap(Class<T> clazz) {
        String key = clazz.getName();
        Object obj = get(key);
        if (obj == null) {
            set(key, new HashMap<Serializable, T>());
        }
        Map<Serializable, T> map = (Map<Serializable, T>) get(key);
        return map;
    }
    // ----------------------------------------- 线程缓存 -----------------------------------------

    public static void remove() {
        threadLocal.remove();
    }

    /**
     * 代码确认使用admin作为当前线程的用户
     */
//    public static void useAdmin() {
//        TenantContextHandler.setUserId("1");
//        TenantContextHandler.setName("Admin");
//        TenantContextHandler.setUsername("超级管理员");
//        TenantContextHandler.setLogin(true);
//    }

}
