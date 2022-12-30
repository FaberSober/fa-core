package com.faber.core.config.mybatis.base;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.faber.core.config.mybatis.methods.DeleteAll;
import com.faber.core.config.mybatis.methods.DeletePermanentById;
import com.faber.core.config.mybatis.methods.SelectByIdPure;

import java.util.List;

/**
 * 自定义Sql注入
 *
 * @author nieqiurong 2018/8/11 20:23.
 */
public class FaSqlInjector extends DefaultSqlInjector {

    @Override
    public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
        List<AbstractMethod> methodList = super.getMethodList(mapperClass, tableInfo);
        //增加自定义方法
        methodList.add(new DeleteAll("deleteAll"));
        methodList.add(new DeletePermanentById("deletePermanentById"));
        methodList.add(new SelectByIdPure("selectByIdPure"));
        return methodList;
    }
}
