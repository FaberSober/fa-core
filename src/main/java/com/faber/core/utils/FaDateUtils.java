package com.faber.core.utils;

import cn.hutool.core.date.DateUtil;

import java.util.Date;

/**
 * @author Farando
 * @date 2023/2/19 17:27
 * @description
 */
public class FaDateUtils {

    /**
     * 用于记录日志的时间格式：yyyy-MM-dd HH:mm:ss.SSS
     * @return
     */
    public static String nowLog() {
        return DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss.SSS");
    }

}
