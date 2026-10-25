package com.example.aidoc.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 文档处理状态。
 */
public enum DocumentStatus {

    PROCESSING(0, "处理中"),
    READY(1, "已就绪"),
    FAILED(2, "处理失败");

    @EnumValue
    private final int code;

    private final String desc;

    DocumentStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
