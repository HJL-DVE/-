package com.teaclub.cloud.feign.client;

import com.teaclub.cloud.core.api.Result;
import com.teaclub.cloud.core.api.ResultCode;
import com.teaclub.cloud.core.exception.BizException;
import org.springframework.cloud.openfeign.FeignClient;


/**
 * 内部服务间调用客户端基类。
 *
 * <p>约定：
 * <ul>
 *   <li>所有内部调用都必须带 {@code X-Internal-Token}，否则下游拒绝</li>
 *   <li>用户身份与 Trace ID 通过请求头透传</li>
 *   <li>有副作用的接口必须携带业务单号，由下游做幂等</li>
 * </ul>
 *
 * <p><b>重试约束</b>：本基类默认禁用自动重试。
 * 资金扣款、支付创建、退款等操作<b>不得</b>开启自动重试，
 * 必须通过业务单号幂等 + 主动查询结果来保证一致性。
 *
 * @param <T> 返回数据类型
 */
@FeignClient(name = "placeholder", path = "/api")
public interface InternalFeignClient<T> {

    /**
     * 校验下游返回结果，失败时抛出统一异常。
     *
     * <p>放在 default 方法中，所有内部客户端可直接调用。
     */
    default T unwrap(Result<T> result) {
        if (result == null) {
            throw new BizException(ResultCode.DOWNSTREAM_UNAVAILABLE);
        }
        if (result.getCode() != 0) {
            throw new BizException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }
}
