package com.faber.core.license;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class LicenseFileCodec {

    public static final int VERSION = 1;
    public static final int MAX_BYTES = 64 * 1024;

    public LicenseInfo read(InputStream input) {
        if (input == null) {
            throw new LicenseFileException("License 文件为空");
        }
        try {
            byte[] content = input.readNBytes(MAX_BYTES + 1);
            return read(content);
        } catch (IOException e) {
            throw new LicenseFileException("读取 License 文件失败", e);
        }
    }

    public LicenseInfo read(byte[] content) {
        if (content == null || content.length == 0) {
            throw new LicenseFileException("License 文件为空");
        }
        if (content.length > MAX_BYTES) {
            throw new LicenseFileException("License 文件超过 64 KB 限制");
        }

        try {
            JSONObject root = JSON.parseObject(new String(content, StandardCharsets.UTF_8));
            if (root == null) {
                throw new LicenseFileException("License 文件格式错误");
            }
            Object version = root.get("version");
            if (!(version instanceof Number number)
                    || number.longValue() != VERSION
                    || number.doubleValue() != VERSION) {
                throw new LicenseFileException("不支持的 License 文件版本");
            }

            JSONObject payload = root.getJSONObject("payload");
            if (payload == null || payload.containsKey("signature")) {
                throw new LicenseFileException("License payload 格式错误");
            }
            Object signature = root.get("signature");
            if (!(signature instanceof String value) || value.isBlank()) {
                throw new LicenseFileException("License 签名不能为空");
            }

            LicenseInfo licenseInfo = JSON.parseObject(payload.toJSONString(), LicenseInfo.class);
            if (licenseInfo == null) {
                throw new LicenseFileException("License payload 为空");
            }
            licenseInfo.setSignature(value);
            return licenseInfo;
        } catch (LicenseFileException e) {
            throw e;
        } catch (Exception e) {
            throw new LicenseFileException("License 文件格式错误", e);
        }
    }

    public byte[] write(LicenseInfo licenseInfo) {
        if (licenseInfo == null || licenseInfo.getSignature() == null || licenseInfo.getSignature().isBlank()) {
            throw new LicenseFileException("License 签名不能为空");
        }
        JSONObject root = new JSONObject();
        root.put("version", VERSION);
        root.put("payload", JSON.parseObject(licenseInfo.canonicalPayload()));
        root.put("signature", licenseInfo.getSignature());
        return root.toJSONString().getBytes(StandardCharsets.UTF_8);
    }
}
