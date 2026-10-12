package com.faber.core.license;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LicenseFileCodecTest {

    private final LicenseFileCodec codec = new LicenseFileCodec();

    @Test
    void readsVersionOneLicenseFile() {
        LicenseInfo result = codec.read(fileBytes(license("signature")));

        assertEquals("license-1", result.getLicenseId());
        assertEquals(LicenseMode.OFFLINE, result.getMode());
        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), result.getExpireAt());
        assertEquals("signature", result.getSignature());
        assertEquals(Set.of("ai"), result.getFeatures());
    }

    @Test
    void rejectsUnsupportedVersionAndEmbeddedSignature() {
        JSONObject root = JSON.parseObject(new String(fileBytes(license("signature")), StandardCharsets.UTF_8));
        root.put("version", 2);
        assertThrows(LicenseFileException.class, () -> codec.read(root.toJSONString().getBytes(StandardCharsets.UTF_8)));

        root.put("version", 1);
        root.getJSONObject("payload").put("signature", "nested");
        assertThrows(LicenseFileException.class, () -> codec.read(root.toJSONString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsEmptyAndOversizedContent() {
        assertThrows(LicenseFileException.class, () -> codec.read(new byte[0]));
        assertThrows(LicenseFileException.class, () -> codec.read(new byte[LicenseFileCodec.MAX_BYTES + 1]));
    }

    private static byte[] fileBytes(LicenseInfo license) {
        JSONObject root = new JSONObject();
        root.put("version", LicenseFileCodec.VERSION);
        root.put("payload", JSON.parseObject(license.canonicalPayload()));
        root.put("signature", license.getSignature());
        return root.toJSONString().getBytes(StandardCharsets.UTF_8);
    }

    private static LicenseInfo license(String signature) {
        return LicenseInfo.builder()
                .licenseId("license-1")
                .licenseKey("key-1")
                .product("fa-admin")
                .customer("customer-1")
                .machineId("machine-1")
                .mode(LicenseMode.OFFLINE)
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expireAt(Instant.parse("2027-01-01T00:00:00Z"))
                .status(LicenseState.ACTIVE)
                .features(Set.of("ai"))
                .signature(signature)
                .build();
    }
}
