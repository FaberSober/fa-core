package com.faber.core.utils;

import com.faber.core.utils.vo.FaGeoRectVo;

/**
 * 地理GEO相关帮助类
 */
public class FaGeoUtils {

    /**
     * 根据经纬度和半径 计算
     * @param longitude 经度
     * @param latitude 纬度
     * @param radius 搜索半径 m
     * @return {@link FaGeoRectVo}
     */
    public static FaGeoRectVo lngLatCalculation(Double longitude, Double latitude, Integer radius) {
        // 赤道周长24901英里 1609是转换成米的系数
        double degree = (24901 * 1609) / 360.0;
        double radiusMile = radius;
        double dpmLat = 1 / degree;
        double radiusLat = dpmLat * radiusMile;
        double minLat = latitude - radiusLat;
        double maxLat = latitude + radiusLat;
        double mpdLng = degree * Math.cos(latitude * (Math.PI / 180));
        double dpmLng = 1 / mpdLng;
        double radiusLng = dpmLng * radiusMile;
        double minLng = longitude - radiusLng;
        double maxLng = longitude + radiusLng;

        return new FaGeoRectVo(minLat, maxLat, minLng, maxLng);
    }

}
