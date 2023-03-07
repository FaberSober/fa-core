package com.faber.core.utils;

/**
 * @author Farando
 * @date 2023/3/6 16:04
 * @description
 */
public class FaKeyUtils {

    public static String getTokenKey(String token) {
        return "login:token:" + token;
    }

}
