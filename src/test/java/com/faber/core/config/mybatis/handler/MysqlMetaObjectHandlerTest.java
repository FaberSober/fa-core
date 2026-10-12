package com.faber.core.config.mybatis.handler;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.faber.core.bean.BaseTnDelEntity;
import com.faber.core.constant.FaSetting;
import com.faber.core.context.BaseContextHandler;
import com.faber.core.context.TenantContext;
import com.faber.core.exception.BuzzException;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MysqlMetaObjectHandlerTest {

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), TenantEntity.class
        );
    }

    @AfterEach
    void tearDown() {
        BaseContextHandler.remove();
    }

    @Test
    void insertFillOverridesClientTenantIdWithContextTenant() {
        TenantContext.setTenantId("tenant-a");
        TenantEntity entity = new TenantEntity();
        entity.setTenantId("client-tenant");

        new MysqlMetaObjectHandler(setting(true)).insertFill(SystemMetaObject.forObject(entity));

        assertEquals("tenant-a", entity.getTenantId());
    }

    @Test
    void enabledTenantModeRejectsInsertWithoutContext() {
        TenantEntity entity = new TenantEntity();
        entity.setTenantId("client-tenant");

        assertThrows(BuzzException.class,
                () -> new MysqlMetaObjectHandler(setting(true))
                        .insertFill(SystemMetaObject.forObject(entity)));
    }

    @Test
    void disabledTenantModeLeavesEntityValueUntouched() {
        TenantEntity entity = new TenantEntity();
        entity.setTenantId("client-tenant");

        new MysqlMetaObjectHandler(setting(false)).insertFill(SystemMetaObject.forObject(entity));

        assertEquals("client-tenant", entity.getTenantId());
    }

    private FaSetting setting(boolean enabled) {
        FaSetting setting = new FaSetting();
        FaSetting.Tenant tenant = new FaSetting.Tenant();
        tenant.setEnabled(enabled);
        setting.setTenant(tenant);
        return setting;
    }

    @TableName("test_tenant_entity")
    private static class TenantEntity extends BaseTnDelEntity {
        @TableId
        private String id;
    }
}
