package com.faber.core.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/** 日志归档保留策略。 */
@Getter
public enum LogArchiveRetentionPolicyEnum implements IEnum<String> {
    FOREVER("FOREVER", "永久保留"),
    MONTHS("MONTHS", "保留指定月数");

    @JsonValue
    @EnumValue
    private final String value;
    private final String desc;

    LogArchiveRetentionPolicyEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
