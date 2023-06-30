package com.faber.core.utils;

import cn.hutool.json.JSONObject;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;

/**
 * Spring Resource Helper Class
 *
 * @author xu.pengfei
 * @date 2022/11/28 14:32
 */
public class FaResourceUtils {

    public static String getResourceString(Resource resource) throws IOException {
        //获得文件流，因为在jar文件中，不能直接通过文件资源路径拿到文件，但是可以在jar包中拿到文件流
        InputStream stream = resource.getInputStream();
        StringBuilder buffer = new StringBuilder();
        byte[] bytes = new byte[1024];
        try {
            for (int n; (n = stream.read(bytes)) != -1; ) {
                buffer.append(new String(bytes, 0, n));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer.toString();
    }

    /**
     * 读取jar打包内部的资源文件。
     *
     * @param resourceLocation 如："classpath:data/updateLog.json"
     * @return 返回文件字符串
     * @throws IOException
     */
    public static String getResourceString(String resourceLocation) throws IOException {
        // //获得文件流，因为在jar文件中，不能直接通过文件资源路径拿到文件，但是可以在jar包中拿到文件流。（一定要用流，不要尝试去拿到绝对路径，否则报错！）
        // https://blog.csdn.net/xiaowanziwuha/article/details/105378559

        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        // Resource[] resources = resolver.getResources("templates/layout/email.html");
        Resource resource = resolver.getResource(resourceLocation);

        return getResourceString(resource);
        // 以下的方法，在打包后，取jar内部jar会报错
        // class path resource [sql/1.0.0_base_data.sql] cannot be resolved to absolute file path because it does not reside in the file system: jar:file:/opt/kgcesi-v3/fa-admin.jar!/BOOT-INF/lib/fa-base-3.0-SNAPSHOT.jar!/sql/1.0.0_base_data.sql
//        File file = ResourceUtils.getFile(resourceLocation);
//        return FileUtil.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * 读取jar打包内部的资源文件。
     *
     * @param resourceLocation 如："classpath:data/updateLog.json"
     * @return 返回文件JSONObject
     * @throws IOException
     */
    public static JSONObject getResourceJson(String resourceLocation) throws IOException {
        String fileStr = getResourceString(resourceLocation);
        return new JSONObject(fileStr);
    }

}
