package com.faber.core.config.dbinit;

import cn.hutool.core.util.ClassUtil;
import cn.hutool.extra.spring.SpringUtil;
import org.springframework.stereotype.Service;

/**
 * @author Farando
 * @date 2023/2/18 20:13
 * @description
 */
@Service
public class DbInitService {

    public void initDb() {
        ClassUtil.scanPackageBySuper("com.faber", DbInit.class)
                .forEach(clazz -> {
                    DbInit dbInit = (DbInit) SpringUtil.getBean(clazz);
                    dbInit.execute();
                });
    }

}
