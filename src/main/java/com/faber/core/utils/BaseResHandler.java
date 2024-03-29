package com.faber.core.utils;

import com.faber.core.context.BaseContextHandler;
import com.faber.core.context.TnTenantContextHandler;
import com.faber.core.vo.msg.Ret;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用返回共有接口
 *
 * @author xu.pengfei
 * @date 2022/11/28 14:28
 */
public class BaseResHandler {

    public String getCurrentUserName() {
        return BaseContextHandler.getUsername();
    }

    /**
     * 获取当前登录的admin账户
     * @return
     */
    public String getCurrentUserId() {
        return BaseContextHandler.getUserId();
    }

    /**
     * 获取当前登录的tenant账户
     * @return
     */
    public Long getCurrTenantUserId() {
        return TnTenantContextHandler.getUserId();
    }

    protected <T> Ret<T> ok() {
        return new Ret<T>().rel();
    }

    protected <T> Ret<T> ok(T data) {
        return new Ret<T>().data(data);
    }

    protected Ret<List<Map<String, Object>>> okDatav(Object data) {
        Map<String, Object> map = new HashMap<>();
        map.put("value", data);

        List<Map<String, Object>> list = new ArrayList<>();
        list.add(map);
        return new Ret<List<Map<String, Object>>>().data(list);
    }
}
