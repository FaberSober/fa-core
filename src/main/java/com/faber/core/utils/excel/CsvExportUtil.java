package com.faber.core.utils.excel;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

public class CsvExportUtil<T> {

    public void exportToCsv(List<T> dataList, String[] headers, String[] fields, String delimiter, String filePath) throws IOException, NoSuchFieldException, IllegalAccessException {
        try (FileWriter writer = new FileWriter(filePath)) {
            // 写入表头
            writeLine(writer, headers, delimiter);

            // 写入数据行
            for (T data : dataList) {
                String[] values = new String[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    Field field = data.getClass().getDeclaredField(fields[i]);
                    field.setAccessible(true);
                    Object value = field.get(data);
                    values[i] = (value != null) ? value.toString() : "";
                }
                writeLine(writer, values, delimiter);
            }
        }
    }

    private void writeLine(FileWriter writer, String[] values, String delimiter) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            sb.append(values[i]);
            if (i < values.length - 1) {
                sb.append(delimiter);
            }
        }
        sb.append("\n");
        writer.write(sb.toString());
    }
}
