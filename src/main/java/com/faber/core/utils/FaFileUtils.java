package com.faber.core.utils;

import cn.hutool.core.date.DateUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.faber.core.file.impl.FileHelperLocal;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ResourceUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


/**
 * 文件帮助类
 * @author xu.pengfei
 * @date 2022/11/28 14:30
 */
public class FaFileUtils {

    public static final List<String> IMG_EXTS = Arrays.asList("png", "jpg", "jpeg", "gif");


    public static String getAbsolutePath() throws IOException {
        // 开发环境获取编译class路径
        if ("dev".equals(SpringUtil.getActiveProfile())) {
            File path = new File(ResourceUtils.getURL("classpath:").getPath());
            if(!path.exists()) path = new File("");
            return path.getAbsolutePath();
        }

        // 执行jar的环境获取jar的路径
        ApplicationHome home = new ApplicationHome(FileHelperLocal.class);
        File jarFile = home.getSource();
        String path = jarFile.getParentFile().toString();
        return path;
    }

    /**
     * 下载文件
     * @param file
     * @throws IOException
     */
    public static void downloadFile(File file) throws IOException {
        downloadFile(file, file.getName());
    }

    /**
     * 下载文件
     * @param file
     * @throws IOException
     */
    public static void downloadFile(File file, String filename) throws IOException {
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();
        InputStream in = new FileInputStream(file);

        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode(filename, "UTF-8");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName);
        response.setHeader("fa-filename", fileName);
        //4.获取要下载的文件输入流
        int len = 0;
        //5.创建数据缓冲区
        byte[] buffer = new byte[1024];
        //6.通过response对象获取OutputStream流
        OutputStream out = response.getOutputStream();
        //7.将FileInputStream流写入到buffer缓冲区
        while ((len = in.read(buffer)) > 0) {
            //8.使用OutputStream将缓冲区的数据输出到客户端浏览器
            out.write(buffer, 0, len);
        }
        in.close();
    }

    /**
     * 下载资源文件
     * @param response
     * @param filePath
     * @throws IOException
     */
    public static void downloadResourceFile(HttpServletResponse response, String filePath, String filename) throws IOException {
        InputStream in = new ClassPathResource(filePath).getInputStream();

        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode(filename, "UTF-8");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName);
        //4.获取要下载的文件输入流
        int len = 0;
        //5.创建数据缓冲区
        byte[] buffer = new byte[1024];
        //6.通过response对象获取OutputStream流
        OutputStream out = response.getOutputStream();
        //7.将FileInputStream流写入到buffer缓冲区
        while ((len = in.read(buffer)) > 0) {
            //8.使用OutputStream将缓冲区的数据输出到客户端浏览器
            out.write(buffer, 0, len);
        }
        in.close();
    }

    /**
     * 在文件名后追加后缀
     * @param fileName
     * @return
     */
    public static String addSuffixToFileName(String fileName, String suffix) {
        if (fileName == null) return fileName;

        if (fileName.contains(".")) {
            int index = fileName.lastIndexOf(".");
            return fileName.substring(0, index) + suffix + fileName.substring(index);
        }
        return fileName + suffix;
    }

    /**
     * 在文件名后追加时间戳。如xxx.jpg修改为xxx_20220815120000.jpg
     * @param fileName
     * @return
     */
    public static String addTimestampToFileName(String fileName) {
        String now = DateUtil.format(new Date(), "yyyyMMddHHmmss");

        if (fileName == null) return now;

        if (fileName.contains(".")) {
            int index = fileName.lastIndexOf(".");
            return fileName.substring(0, index) + "_" + now + fileName.substring(index);
        }
        return fileName + "_" + now;
    }

    /**
     * 判断是否是图片文件
     * @param fileExt
     * @return
     */
    public static boolean isImg(String fileExt) {
        return IMG_EXTS.contains(fileExt.toLowerCase());
    }

}
