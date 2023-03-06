package com.faber.core.tenant.bean;

import com.alibaba.excel.annotation.ExcelProperty;
import com.faber.core.annotation.SqlEquals;
import com.faber.core.bean.BaseDelEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * @author Farando
 * @date 2023/3/6 17:04
 * @description
 */
@Data
@ToString
@EqualsAndHashCode(callSuper = false)
public class TnBaseDelEntity extends BaseDelEntity {

    @SqlEquals
    @ExcelProperty("企业ID")
    private Integer corpId;

    @SqlEquals
    @ExcelProperty("租户ID")
    private Integer tenantId;

}
