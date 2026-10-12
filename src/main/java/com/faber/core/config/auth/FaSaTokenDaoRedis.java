package com.faber.core.config.auth;

import cn.dev33.satoken.dao.SaTokenDaoRedisJackson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * sa-token存储redis无法自定义redis key前缀的问题，参考issue：
 * https://github.com/dromara/Sa-Token/issues/203
 * https://github.com/dromara/Sa-Token/issues/142
 *
 * @author xu.pengfei
 * @date 2024-04-22 10:50:00
 */
@Primary
@Component
public class FaSaTokenDaoRedis extends SaTokenDaoRedisJackson {

    @Value("${spring.data.redis.prefix}")
    private String redisPrefix;

    private String getKey(String key) {
        return redisPrefix + ":" + key;
    }

    public String get(String key) {
        return super.get(getKey(key));
    }

    public void set(String key, String value, long timeout) {
        super.set(getKey(key), value, timeout);
    }

    public void update(String key, String value) {
        // 父类 update 会虚调用 getTimeout/set，传入已加前缀的 key 会再次添加前缀。
        long timeout = getTimeout(key);
        if (timeout != NOT_VALUE_EXPIRE) set(key, value, timeout);
    }

    public void delete(String key) {
        super.delete(getKey(key));
    }

    public long getTimeout(String key) {
        return super.getTimeout(getKey(key));
    }

    public void updateTimeout(String key, long timeout) {
        if (timeout == NEVER_EXPIRE) {
            // 父类永久有效分支也会虚调用 get/set，保持逻辑 key 只转换一次。
            long remaining = getTimeout(key);
            if (remaining != NOT_VALUE_EXPIRE && remaining != NEVER_EXPIRE) set(key, get(key), timeout);
        } else {
            super.updateTimeout(getKey(key), timeout);
        }
    }

    public Object getObject(String key) {
        return super.getObject(getKey(key));
    }

    public void setObject(String key, Object object, long timeout) {
        super.setObject(getKey(key), object, timeout);
    }

    public void updateObject(String key, Object object) {
        long timeout = getObjectTimeout(key);
        if (timeout != NOT_VALUE_EXPIRE) setObject(key, object, timeout);
    }

    public void deleteObject(String key) {
        super.deleteObject(getKey(key));
    }

    public long getObjectTimeout(String key) {
        return super.getObjectTimeout(getKey(key));
    }

    public void updateObjectTimeout(String key, long timeout) {
        if (timeout == NEVER_EXPIRE) {
            long remaining = getObjectTimeout(key);
            if (remaining != NOT_VALUE_EXPIRE && remaining != NEVER_EXPIRE) setObject(key, getObject(key), timeout);
        } else {
            super.updateObjectTimeout(getKey(key), timeout);
        }
    }

    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        return super.searchData(getKey(prefix), keyword, start, size, sortType);
    }

}
