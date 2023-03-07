package com.faber.core.config.mybatis.handler;

import cn.hutool.core.util.ClassUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.faber.core.bean.BaseCrtEntity;
import com.faber.core.bean.BaseUpdEntity;
import com.faber.core.context.BaseContextHandler;
import com.faber.core.context.TnTenantContextHandler;
import com.faber.core.tenant.bean.TnBaseCrtEntity;
import com.faber.core.tenant.bean.TnBaseUpdEntity;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * 测试，自定义元对象字段填充控制器，实现公共字段自动写入
 */
public class MysqlMetaObjectHandler implements MetaObjectHandler {

    /**
     * 测试 user 表 name 字段为空自动填充
     */
    @Override
    public void insertFill(MetaObject metaObject) {
//        Object crtTime = this.getFieldValByName("crtTime", metaObject);
        // tenant
        if (TnBaseCrtEntity.class.isAssignableFrom(metaObject.getOriginalObject().getClass())) {
            if (TnTenantContextHandler.getLogin()) {
                this.strictInsertFill(metaObject, "crtUser", String.class, TnTenantContextHandler.getUserId() + "");
                this.strictInsertFill(metaObject, "crtName", String.class, TnTenantContextHandler.getName());

                this.strictInsertFill(metaObject, "tenantId", Integer.class, TnTenantContextHandler.getTenantId());
                this.strictInsertFill(metaObject, "corpId", Integer.class, TnTenantContextHandler.getCorpId());
            }
            fillCrtNormal(metaObject);

            return;
        }

        // admin
        if (BaseCrtEntity.class.isAssignableFrom(metaObject.getOriginalObject().getClass())) {
            if (BaseContextHandler.getLogin()) {
                this.strictInsertFill(metaObject, "crtUser", String.class, BaseContextHandler.getUserId());
                this.strictInsertFill(metaObject, "crtName", String.class, BaseContextHandler.getName());
            }
            fillCrtNormal(metaObject);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // tenant
        if (TnBaseUpdEntity.class.isAssignableFrom(metaObject.getOriginalObject().getClass())) {
            if (TnTenantContextHandler.getLogin()) {
                this.strictInsertFill(metaObject, "updUser", String.class, TnTenantContextHandler.getUserId() + "");
                this.strictInsertFill(metaObject, "updName", String.class, TnTenantContextHandler.getName());

                this.strictInsertFill(metaObject, "tenantId", Integer.class, TnTenantContextHandler.getTenantId());
                this.strictInsertFill(metaObject, "corpId", Integer.class, TnTenantContextHandler.getCorpId());
            }
            this.strictUpdateFill(metaObject, "updTime", LocalDateTime.class, LocalDateTime.now());
            this.strictUpdateFill(metaObject, "updHost", String.class, BaseContextHandler.getIp());

            return;
        }

        // admin
        if (BaseUpdEntity.class.isAssignableFrom(metaObject.getOriginalObject().getClass())) {
            if (BaseContextHandler.getLogin()) {
                this.strictUpdateFill(metaObject, "updUser", String.class, BaseContextHandler.getUserId());
                this.strictUpdateFill(metaObject, "updName", String.class, BaseContextHandler.getName());
            }
            this.strictUpdateFill(metaObject, "updTime", LocalDateTime.class, LocalDateTime.now());
            this.strictUpdateFill(metaObject, "updHost", String.class, BaseContextHandler.getIp());
        }
    }

    private void fillCrtNormal(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "crtTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "crtHost", String.class, BaseContextHandler.getIp());

        this.strictInsertFill(metaObject, "deleted", Boolean.class, false);
    }

}

