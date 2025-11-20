package com.faber.core.vo.tree;

import java.io.Serializable;

import lombok.Data;
import lombok.ToString;

/**
 * 树节点位置变更[排序、父节点]
 */
@Data
@ToString
public class TreePosChangeVo<T extends Serializable> {

    /**
     * 对应实体的ID
     */
    private T key;

    /**
     * 更新排序index
     */
    private Integer index;

    /**
     * 更新排序pid
     */
    private T pid;

}
