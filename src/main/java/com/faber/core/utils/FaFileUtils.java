package com.faber.core.utils;

import cn.hutool.core.date.DateUtil;
import cn.hutool.extra.spring.SpringUtil;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.ResourceUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.Charset;
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

    /**
     * 获取项目路径，开发时用
     * @return
     * @throws IOException
     */
    public static String getProjectRootDir() throws IOException {
        File path = new File(ResourceUtils.getURL("classpath:").getPath());
        return path.getParentFile().getParentFile().getParentFile().getAbsolutePath();
    }

    /**
     * 获取绝对路径。
     * 1. dev环境下返回classpath:路径
     * 2. 其他环境下返回jar执行路径
     * @return
     * @throws IOException
     */
    public static String getAbsolutePath() throws IOException {
        // 开发环境获取编译class路径
        if ("dev".equals(SpringUtil.getActiveProfile())) {
            File path = new File(ResourceUtils.getURL("classpath:").getPath());
            if(!path.exists()) path = new File("");
            return path.getAbsolutePath();
        }

        // 执行jar的环境获取jar的路径
        ApplicationHome home = new ApplicationHome(FaFileUtils.class);
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
     * @param in
     * @throws IOException
     */
    public static void download(InputStream in, String filename) throws IOException {
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();

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
     * 下载str
     * @param str
     * @throws IOException
     */
    public static void download(String str, String filename) throws IOException {
        InputStream in = new ByteArrayInputStream(str.getBytes(Charset.forName("UTF-8")));
        download(in, filename);
    }

    /**
     * 下载文件
     * @param file
     * @throws IOException
     */
    public static void downloadFile(File file, String filename) throws IOException {
        InputStream in = new FileInputStream(file);
        download(in, filename);
    }

    public static void downloadFileShard(File file, String filename) throws IOException {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();

        response.setCharacterEncoding("utf-8");
        //定义文件路径
        InputStream is = null;
        OutputStream os = null;
        try {
            //分片下载
            long fSize = file.length();//获取长度
            String fileName = URLEncoder.encode(filename, "utf-8");

            String contentType = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
//            response.setContentType("application/x-download");
            response.setContentType(contentType);

            if (contentType.startsWith("image")) {
                response.addHeader("Content-Disposition",String.format("inline; filename=\"%1$s\"; filename*=utf-8''%2$s\n", fileName, fileName));
            } else {
                response.addHeader("Content-Disposition","attachment;filename="+fileName);
            }

            //根据前端传来的Range  判断支不支持分片下载
            response.setHeader("Accept-Range","bytes");

            //获取文件大小
            response.setHeader("fa-size",String.valueOf(fSize));
            response.setHeader("fa-filename",fileName);
            //定义断点
            long pos = 0,last = fSize-1,sum = 0;
            //判断前端需不需要分片下载
            if (null != request.getHeader("Range")) {
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                String numRange = request.getHeader("Range").replaceAll("bytes=","");
                String[] strRange = numRange.split("-");
                if (strRange.length == 2){
                    pos = Long.parseLong(strRange[0].trim());
                    last = Long.parseLong(strRange[1].trim());
                    //若结束字节超出文件大小 取文件大小
                    if (last>fSize-1){
                        last = fSize-1;
                    }
                }else {
                    //若只给一个长度  开始位置一直到结束
                    pos = Long.parseLong(numRange.replaceAll("-","").trim());
                }
            }
            long rangeLength = last-pos+1;
            String contentRange = new StringBuffer("bytes").append(pos).append("-").append(last).append("/").append(fSize).toString();
            response.setHeader("Content-Range",contentRange);
            response.setHeader("Content-Length",String.valueOf(rangeLength));
            os = new BufferedOutputStream(response.getOutputStream());
            is = new BufferedInputStream(new FileInputStream(file));
            is.skip(pos);//跳过已读的文件
            byte[] buffer = new byte[1024];
            int lenght = 0;
            //相等证明读完
            while (sum < rangeLength){
                lenght = is.read(buffer,0, (rangeLength-sum)<=buffer.length? (int) (rangeLength - sum) :buffer.length);
                sum = sum+lenght;
                os.write(buffer,0,lenght);
            }
        }finally {
            if (is!= null){
                is.close();
            }
            if (os!=null){
                os.close();
            }
        }
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
