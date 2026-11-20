package com.teaclub.cloud.report.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 跨服务查询的统一出口。
 *
 * <p>为什么不直接用 Feign 接口收每个服务：报表需要的是「一段时间的聚合数字」，
 * 各服务已有的统计接口口径不同（order 用 payable_amount、wallet 按状态），
 * 这里统一走各服务已暴露的 <b>stats/internal</b> 接口，拿到 Result 包裹后解 data。
 * 不直接访问其他服务的数据库 —— 这是阶段八的硬约束。
 */
@Slf4j
@Component
public class ReportFeignClient {

    private final RestTemplate restTemplate;
    private final String internalToken;

    public ReportFeignClient(@Value("${teaclub.security.internal-token:teaclub-internal-default-token}") String internalToken) {
        this.internalToken = internalToken;
        // 连接超时 3s、读超时 10s：报表查询不能拖垮调用方
        var rf = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(3000);
        rf.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(rf);
    }

    /**
     * 调内部统计接口，失败返回 null（报表宁可缺一格也不能整页崩）。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> call(String service, String path) {
        String url = "http://" + service + path;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", internalToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> resp =
                    restTemplate.exchange(url, HttpMethod.GET, entity,
                            new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = resp.getBody();
            if (body == null) {
                return null;
            }
            Object code = body.get("code");
            if (code instanceof Number n && n.intValue() == 0) {
                return (Map<String, Object>) body.get("data");
            }
            log.warn("[REPORT-FEIGN] 业务失败 service={} path={} code={} msg={}",
                    service, path, code, body.get("message"));
            return null;
        } catch (Exception e) {
            log.warn("[REPORT-FEIGN] 调用失败 service={} path={} err={}", service, path, e.getMessage());
            return null;
        }
    }
}
