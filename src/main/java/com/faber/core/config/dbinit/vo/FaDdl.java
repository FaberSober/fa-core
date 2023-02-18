package com.faber.core.config.dbinit.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Farando
 * @date 2023/2/18 20:27
 * @description
 */
@Data
public class FaDdl {

    private long ver;
    private String verNo;
    private String remark;
    private List<FaDdlTableCreate> tableCreateList;

    public FaDdl(long ver, String verNo, String remark) {
        this.ver = ver;
        this.verNo = verNo;
        this.remark = remark;
    }

    public FaDdl addTableCreate(FaDdlTableCreate tableCreate) {
        if (tableCreateList == null) {
            tableCreateList = new ArrayList<>();
        }
        tableCreateList.add(tableCreate);
        return this;
    }

}
