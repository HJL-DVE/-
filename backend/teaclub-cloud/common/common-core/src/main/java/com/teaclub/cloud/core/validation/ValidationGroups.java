package com.teaclub.cloud.core.validation;

/**
 * 参数校验分组。
 *
 * <p>用于区分新增/修改场景的校验规则：
 * <ul>
 *   <li>{@link #CREATE} —— 新增时必填</li>
 *   <li>{@link #UPDATE} —— 修改时必填</li>
 *   <li>{@link #DEFAULT} —— 通用规则（不指定 groups 时生效）</li>
 * </ul>
 */
public interface ValidationGroups {

    /** 新增场景 */
    interface Create extends Default {
    }

    /** 修改场景 */
    interface Update extends Default {
    }

    /** 通用分组 */
    interface Default {
    }
}
