package com.teaclub.cloud.core.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ResultCodeTest {

    @Test
    @DisplayName("错误码全局唯一")
    void codesUnique() {
        Set<Integer> codes = new HashSet<>();
        for (ResultCode rc : ResultCode.values()) {
            assertThat(codes.add(rc.getCode())).as("重复错误码: " + rc.getCode()).isTrue();
        }
    }

    @Test
    @DisplayName("SUCCESS 为 0，其余为 5 位数")
    void codeRanges() {
        assertThat(ResultCode.SUCCESS.getCode()).isZero();
        for (ResultCode rc : ResultCode.values()) {
            if (rc != ResultCode.SUCCESS) {
                assertThat(rc.getCode()).isBetween(10000, 99999);
            }
        }
    }

    @Test
    @DisplayName("每个错误码都有非空消息")
    void messagesPresent() {
        for (ResultCode rc : ResultCode.values()) {
            assertThat(rc.getMessage()).isNotBlank();
        }
    }
}
