package com.faber.core.vo.msg;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 基础返回Response父类
 * @author xu.pengfei
 * @date 2022/11/28 14:43
 */
@Data
public class BaseRet {

    private int status = 200;
    private int code = 200;
    private String message = "";

    public BaseRet() {
    }

    public BaseRet(int status, String message) {
        this.status = status;
        this.code = status;
        this.message = message;
    }
}
