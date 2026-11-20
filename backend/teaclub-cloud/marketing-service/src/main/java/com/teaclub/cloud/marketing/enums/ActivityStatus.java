package com.teaclub.cloud.marketing.enums;

/** 活动状态。 */
public enum ActivityStatus {
    DRAFT("草稿"),
    ENABLED("进行中"),
    DISABLED("已停用"),
    EXPIRED("已过期");

    private final String label;

    ActivityStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
