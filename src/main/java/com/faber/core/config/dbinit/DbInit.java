package com.faber.core.config.dbinit;

import com.faber.core.config.dbinit.vo.FaDdl;

import java.util.List;

/**
 * 继承此接口，实现数据库自动建表、更新表结构
 *
 * @author Farando
 * @date 2023/2/18 20:09
 * @description
 */
public interface DbInit {

    /**
     * 返回模块编码，如：fa-base
     * @return
     */
    String getNo();

    /**
     * 返回模块名称，如：基础模块
     * @return
     */
    String getName();

    List<FaDdl> getDdlList();

}
