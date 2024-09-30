package com.faber.core.util;

import com.faber.core.utils.FaDateUtils;
import org.junit.Test;

public class FaDateUtilsTest {

    @Test
    public void testNormalize() {
        System.out.println(FaDateUtils.normalize("2023/1/1 0:00"));
    }

    @Test
    public void testTimestamp() {
        System.out.println(FaDateUtils.timestampMillToDate(1727484038982L));
    }

}
