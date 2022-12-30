package com.faber.core.config.mybatis.base;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;

import java.io.Serializable;

/**
 * @author K
 * @since 2019/7/9
 */
public interface FaBaseMapper<T> extends BaseMapper<T> {

    /* ↓↓↓↓↓↓↓↓↓↓↓↓↓↓  ↓↓↓↓↓↓↓↓↓↓↓↓↓↓ */

    /**
     * 以下定义的 4个 default method, copy from {@link com.baomidou.mybatisplus.extension.toolkit.ChainWrappers}
     */
    default QueryChainWrapper<T> queryChain() {
        return new QueryChainWrapper<>(this);
    }

    default LambdaQueryChainWrapper<T> lambdaQueryChain() {
        return new LambdaQueryChainWrapper<>(this);
    }

    default UpdateChainWrapper<T> updateChain() {
        return new UpdateChainWrapper<>(this);
    }

    default LambdaUpdateChainWrapper<T> lambdaUpdateChain() {
        return new LambdaUpdateChainWrapper<>(this);
    }

    /**
     * 以下为自己自定义
     */
    int deleteAll();

    /**
     * 物理永久删除
     * @param id
     * @return
     */
    int deletePermanentById(Serializable id);

    /**
     * id查询，不受逻辑删除字段限制
     * @param id
     * @return
     */
    T selectByIdPure(Serializable id);

}
