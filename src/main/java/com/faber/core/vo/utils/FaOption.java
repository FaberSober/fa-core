package com.faber.core.vo.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Select通用返回Option
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FaOption<T> {
    private T id;
    private String name;
}
