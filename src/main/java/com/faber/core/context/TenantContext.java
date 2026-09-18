package com.faber.core.context;

import cn.hutool.core.util.StrUtil;
import com.faber.core.constant.CommonConstants;
import com.faber.core.exception.BuzzException;

/**
 * 当前请求的租户上下文。
 */
public final class TenantContext {

    private TenantContext() {
    }

    public static String getTenantId() {
        return BaseContextHandler.getTenantId();
    }

    public static void setTenantId(String tenantId) {
        BaseContextHandler.setTenantId(tenantId);
    }

    /**
     * 仅清理租户，不影响当前用户上下文。
     */
    public static void clear() {
        BaseContextHandler.setTenantId(null);
    }

    public static String requireTenantId() {
        String tenantId = getTenantId();
        if (StrUtil.isBlank(tenantId)) {
            throw new BuzzException("当前租户上下文为空");
        }
        return tenantId;
    }

    public static boolean isSuperAdmin(String userId) {
        return StrUtil.equals(CommonConstants.SUPER_ADMIN_ID, userId);
    }

    public static boolean isSuperAdminWithoutTenant() {
        return isSuperAdmin(BaseContextHandler.getUserId()) && StrUtil.isBlank(getTenantId());
    }
}
