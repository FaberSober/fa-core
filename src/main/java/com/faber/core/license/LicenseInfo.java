package com.faber.core.license;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LicenseInfo implements Serializable {

    private String licenseId;
    private String licenseKey;
    private String product;
    private String customer;
    private String machineId;
    private LicenseMode mode;
    private Instant issuedAt;
    private Instant expireAt;
    private LicenseState status;
    /** 宽限期秒数，在线授权使用。 */
    private Long gracePeriod;
    @Builder.Default
    private Set<String> features = new TreeSet<>();
    private String signature;

    /**
     * 返回签名用的固定顺序 payload。signature 不参与自身签名。
     */
    public String canonicalPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("licenseId", licenseId);
        payload.put("licenseKey", licenseKey);
        payload.put("product", product);
        payload.put("customer", customer);
        payload.put("machineId", machineId);
        payload.put("mode", mode == null ? null : mode.name());
        payload.put("issuedAt", issuedAt == null ? null : issuedAt.toString());
        payload.put("expireAt", expireAt == null ? null : expireAt.toString());
        payload.put("status", status == null ? null : status.name());
        payload.put("gracePeriod", gracePeriod);
        payload.put("features", features == null ? List.of() : new ArrayList<>(new TreeSet<>(features)));
        return JSON.toJSONString(payload, JSONWriter.Feature.WriteMapNullValue);
    }
}
