package com.faber.core.utils;

/**
 * @author Farando
 * @date 2023/2/19 20:46
 * @description
 */
public class FaDbUtils {

    /**
     * 从数据库连接url中解析数据库名称
     * @param url
     * @return
     */
    public static String getNameFromUrl(String url) {
        String subUrl = url.substring(url.indexOf("//") + 2);

        if (subUrl.contains("?")) {
            return subUrl.substring(subUrl.indexOf("/") + 1, subUrl.indexOf("?"));
        }

        return subUrl.substring(subUrl.indexOf("/") + 1);
    }

}
