package com.faber.core.vo.excel;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ToString
public class CommonImportExcelReqVo extends LinkedHashMap<String, Object> {

    /**
     * 文件ID
     */
    private String fileId;

    public CommonImportExcelReqVo(Map<String, Object> params) {
        this.putAll(params);
        this.fileId = params.get("fileId").toString();
    }


}
