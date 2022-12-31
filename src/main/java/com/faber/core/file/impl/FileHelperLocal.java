package com.faber.core.file.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.faber.core.constant.FaSetting;
import com.faber.core.exception.BuzzException;
import com.faber.core.file.FileHelperImpl;
import com.faber.core.service.ConfigSysService;
import com.faber.core.utils.FaFileUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URLEncoder;


/**
 * 本地文件存储
 *
 * @author xu.pengfei
 * @date 2022/11/28 14:20
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "fa.setting.file.saveType", havingValue = "local")
public class FileHelperLocal implements FileHelperImpl {

    @Resource
    private FaSetting faSetting;

    @Override
    public String upload(InputStream is, String dir, String fileName) throws IOException {
        String fileSavePath = getDirPath() + dir + "/" + DateUtil.today() + "/" + FaFileUtils.addTimestampToFileName(fileName);

        File exportFile = new File(getStorePath(), fileSavePath);
        FileUtils.copyInputStreamToFile(is, exportFile);

        return fileSavePath;
    }

    @Override
    public String getImgPreview(String fileUrl) throws IOException {
        File file = new File(getStorePath(), fileUrl);

        String extName = FileNameUtil.extName(file);
        if (FaFileUtils.isImg(extName)) {
            String previewFileUrl = FaFileUtils.addSuffixToFileName(fileUrl, "_preview");
            File previewFile = new File(getStorePath(), previewFileUrl);

            BufferedImage image = ImgUtil.read(file);
            int width = image.getWidth();
            int height = image.getHeight();

            // small image, no need to create preview image
            if (width <= 200 && height <= 200) {
                return fileUrl;
            }

            int toWidth = 200;
            int toHeight = 200;
            if (width > height) {
                toHeight = (int) Math.floor(height / (double) width * 200);
            } else {
                toWidth = (int) Math.floor(width / (double) height * 200);
            }

            ImgUtil.scale(
                    file,
                    previewFile,
                    toWidth, toHeight,
                    null
            );

            return previewFileUrl;
        }
        return null;
    }

    @Override
    public void delete(String filePath) throws IOException {
        File file = new File(getStorePath(), filePath);
        if (file.exists()) {
            file.delete();
        }
    }

    public static String getStorePath() throws IOException {
        ConfigSysService configSysService = SpringUtil.getBean(ConfigSysService.class);
        return configSysService.getStoreLocalPath();
    }

    /**
     * 获取文件存储路径
     *
     * @return
     */
    private String getDirPath() {
        return "/" + faSetting.getFile().getPrefix() + "/";
    }

    public static File getLocalFileSavePath(String filePath) throws IOException {
        if (filePath.contains("..")) {
            throw new BuzzException("非法文件名");
        }

        return new File(getStorePath(), filePath);
    }


    /**
     * 根据文件路径，获取存储文件，返回到http流进行下载
     *
     * @param filePath
     * @throws IOException
     */
    public static void getLocalFilePath(String filePath) throws IOException {
        if (filePath.contains("..")) {
            throw new BuzzException("非法文件名");
        }

        File file = new File(getStorePath(), filePath);

        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode(file.getName(), "UTF-8");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName);

        //4.获取要下载的文件输入流
        InputStream in = new FileInputStream(file);
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

}
