package com.faber.core.config.auth;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpLogic;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FaSaTokenDaoRedisTest {
    private FaSaTokenDaoRedis dao;
    private final Map<String, String> strings = new HashMap<>();
    private final Map<String, byte[]> objects = new HashMap<>();
    private final Map<String, Class<?>> types = new HashMap<>();
    private final Map<String, Long> ttl = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        dao = new FaSaTokenDaoRedis();
        ReflectionTestUtils.setField(dao, "redisPrefix", "test");
        dao.stringRedisTemplate = mock(StringRedisTemplate.class);
        dao.objectRedisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, String> stringOps = mock(ValueOperations.class);
        ValueOperations<String, Object> objectOps = mock(ValueOperations.class);
        when(dao.stringRedisTemplate.opsForValue()).thenReturn(stringOps);
        when(dao.objectRedisTemplate.opsForValue()).thenReturn(objectOps);
        when(dao.stringRedisTemplate.getExpire(anyString())).thenAnswer(i -> ttl.getOrDefault(i.getArgument(0), -2L));
        when(dao.objectRedisTemplate.getExpire(anyString())).thenAnswer(i -> ttl.getOrDefault(i.getArgument(0), -2L));
        when(stringOps.get(anyString())).thenAnswer(i -> strings.get(i.getArgument(0)));
        when(objectOps.get(anyString())).thenAnswer(i -> {
            String key = i.getArgument(0);
            return objects.containsKey(key) ? mapper.readValue(objects.get(key), types.get(key)) : null;
        });
        doAnswer(i -> {
            strings.put(i.getArgument(0), i.getArgument(1));
            ttl.put(i.getArgument(0), i.getArgument(2));
            return null;
        }).when(stringOps).set(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        doAnswer(i -> {
            strings.put(i.getArgument(0), i.getArgument(1));
            ttl.put(i.getArgument(0), -1L);
            return null;
        }).when(stringOps).set(anyString(), anyString());
        doAnswer(i -> {
            saveObject(i.getArgument(0), i.getArgument(1), i.getArgument(2));
            return null;
        }).when(objectOps).set(anyString(), any(), anyLong(), eq(TimeUnit.SECONDS));
        doAnswer(i -> {
            saveObject(i.getArgument(0), i.getArgument(1), -1L);
            return null;
        }).when(objectOps).set(anyString(), any());
    }

    private void saveObject(String key, Object value, long timeout) throws Exception {
        // 序列化快照模拟 Redis，不能让 Java 对象引用共享掩盖 updateObject 未写入的问题。
        objects.put(key, mapper.writeValueAsBytes(value));
        types.put(key, value instanceof Map ? Map.class : value.getClass());
        ttl.put(key, timeout);
    }

    @Test
    void stringUpdatesUseOnePrefixAndPreserveTtl() {
        dao.set("token", "user", 60);
        dao.update("token", "revoked");
        assertEquals("revoked", dao.get("token"));
        assertEquals(60, dao.getTimeout("token"));
        dao.updateTimeout("token", -1);
        assertEquals(-1, dao.getTimeout("token"));
        assertEquals(1, strings.size());
        assertTrue(strings.containsKey("test:token"));
    }

    @Test
    void objectUpdatesUseOnePrefixAndPreserveTtl() {
        dao.setObject("session", Map.of("device", "initial"), 60);
        dao.updateObject("session", Map.of("device", "web"));
        assertEquals(Map.of("device", "web"), dao.getObject("session"));
        assertEquals(60, dao.getObjectTimeout("session"));
        dao.updateObjectTimeout("session", -1);
        assertEquals(-1, dao.getObjectTimeout("session"));
        assertEquals(1, objects.size());
        assertTrue(objects.containsKey("test:session"));
    }

    @Test
    void missingEntriesAreNotRecreatedByUpdate() {
        dao.update("missing", "value");
        dao.updateObject("missing", Map.of("device", "web"));
        dao.updateTimeout("missing", -1);
        dao.updateObjectTimeout("missing", -1);
        assertTrue(strings.isEmpty());
        assertTrue(objects.isEmpty());
    }

    @Test
    void actualSaTokenLoginPersistsDeviceAndSharedTokenIndex() {
        SaTokenDao previous = SaManager.getSaTokenDao();
        try {
            SaManager.setSaTokenDao(dao);
            StpLogic logic = new StpLogic("redis-prefix-test");
            logic.setConfig(new SaTokenConfig().setIsConcurrent(true).setIsShare(true).setTimeout(3600));
            String web = logic.createLoginSession("1", new SaLoginModel().setDevice("web"));
            assertEquals("web", logic.getLoginDeviceByToken(web));
            assertEquals(web, logic.createLoginSession("1", new SaLoginModel().setDevice("web")));
            String portal = logic.createLoginSession("1", new SaLoginModel().setDevice("portal"));
            assertEquals("portal", logic.getLoginDeviceByToken(portal));
            assertEquals(1, logic.getTokenValueListByLoginId("1", "web").size());
        } finally {
            SaManager.setSaTokenDao(previous);
        }
    }
}
