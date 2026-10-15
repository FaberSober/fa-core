package com.faber.core.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ConfigSysQiniuAccessTypeEnum implements IEnum<String> {
    PUBLIC("PUBLIC", "公开"),
    PRIVATE("PRIVATE", "私有");

    @JsonValue
    @EnumValue
    private final String value;
    private final String desc;

    ConfigSysQiniuAccessTypeEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @JsonCreator
    public static ConfigSysQiniuAccessTypeEnum fromValue(String value) {
        return valueOf(value);
    }
}
