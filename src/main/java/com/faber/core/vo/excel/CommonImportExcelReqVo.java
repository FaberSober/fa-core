package com.faber.core.vo.excel;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
@ToString
public class CommonImportExcelReqVo implements Serializable {

    /**
     * 文件ID
     */
    private String fileId;

}
