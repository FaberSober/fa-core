package com.faber.core.config.storage;

import cn.xuyanwu.spring.file.storage.FileStorageService;
import cn.xuyanwu.spring.file.storage.platform.LocalPlusFileStorage;
import com.faber.core.service.ConfigSysService;
import com.faber.core.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * 启动执行器：应用启动后，扫描数据库中存储的配置，使用spring-file-storage进行动态配置
 * @author xu.pengfei
 * @create 2023/01/13
 */
@Slf4j
@Configuration
public class FileStorageRunner implements CommandLineRunner  {

    @Autowired
    private StorageService storageService;

    @Override
    public void run(String... args) {
        storageService.syncStorageDatabaseConfig();
    }
}
