package com.faber.core.utils;

import cn.hutool.core.date.DateUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.faber.core.annotation.FaModalName;
import com.github.pagehelper.PageInfo;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FaExcelUtils {

    /**
     * response写入下载Excel文件流
     *
     * @param clazz
     * @param list
     * @throws IOException
     */
    public static <T> void sendFileExcel(Class<T> clazz, List<T> list) throws IOException {
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();

        FaModalName anno = clazz.getAnnotation(FaModalName.class);

        String fileName = DateUtil.now() + "";
        if (anno != null) {
            fileName = anno.name() + "_" + fileName;
        }

        // 这里注意 有同学反应使用swagger 会导致各种问题，请直接用浏览器或者用postman
        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding("utf-8");
        // 这里URLEncoder.encode可以防止中文乱码 当然和easyexcel没有关系
        fileName = URLEncoder.encode(fileName, "UTF-8");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");
        response.setHeader("fa-filename", fileName + ".xlsx");

        EasyExcel
                .write(response.getOutputStream(), clazz)
                .registerWriteHandler(EasyExcelUtils.genHeaderWriteStyle())
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
//                .registerConverter(new BaseEnumConverter())
//                .registerConverter(new LocalDateTimeConverter())
                .sheet("Sheet1")
                .doWrite(list);
    }

    public static <T, E> void writeExcelPage(String fileName, Class<E> clazzExcel, LambdaQueryChainWrapper<T> wrapper, Function<? super T, ? extends E> mapper) {
        writeExcelPage(fileName, clazzExcel, wrapper, mapper, 1000);
    }

    /**
     * 分页查询数据库，然后写入Excel。
     * @param fileName excel文件路径
     * @param clazzExcel 写入Excel的Bean Class
     * @param wrapper
     * @param mapper
     * @param pageSize 分页查询每页查询数量
     * @param <T> 数据库查询Bean Class
     * @param <E> 写入Excel的Bean Class
     */
    public static <T, E> void writeExcelPage(String fileName, Class<E> clazzExcel, LambdaQueryChainWrapper<T> wrapper, Function<? super T, ? extends E> mapper, int pageSize) {
        // 这里 需要指定写用哪个class去写
        ExcelWriterBuilder builder = EasyExcel.write(fileName, clazzExcel);

        if (fileName.endsWith(".csv")) {
            builder.excelType(ExcelTypeEnum.CSV);
        }

        try (ExcelWriter excelWriter = builder.build()) {
            // 这里注意 如果同一个sheet只要创建一次
            WriteSheet writeSheet = EasyExcel.writerSheet().build();

            // 根据数据库分页去调用写入
            FaDbUtils.loopPage(
                    wrapper,
                    pageInfo -> {
                        List<T> dbList = pageInfo.getList();
                        List<E> voList = dbList.stream().map(mapper).collect(Collectors.toList());
                        excelWriter.write(voList, writeSheet);
                    },
                    pageSize
            );
        }
    }

    /**
     * 简单读取Excel内容
     * @param file
     * @param clazz
     * @param consumer
     * @param <T>
     */
    public static <T> void simpleRead(File file, Class<T> clazz, Consumer<T> consumer) {
        EasyExcel.read(file, clazz, new AnalysisEventListener<T>() {

            @Override
            public void invoke(T data, AnalysisContext context) {
                consumer.accept(data);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {

            }
        }).sheet().doRead();
    }

    public static void simpleRead(File file, Consumer<Map<Integer, Object>> consumer) {
        EasyExcel.read(file, new ReadListener<Map<Integer, Object>>() {
            @Override
            public void invoke(Map<Integer, Object> data, AnalysisContext context) {
                consumer.accept(data);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {

            }
        }).sheet().doRead();
    }

}
