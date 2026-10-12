package com.faber.core.license;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class OfflineLicenseService {

    private final LicenseProperties properties;
    private final LicenseFileCodec codec;
    private final LicenseManager licenseManager;
    private final OfflineLicenseStore store;

    public OfflineLicenseService(LicenseProperties properties,
                                  LicenseFileCodec codec,
                                  LicenseManager licenseManager,
                                  OfflineLicenseStore store) {
        this.properties = properties;
        this.codec = codec;
        this.licenseManager = licenseManager;
        this.store = store;
    }

    public LicenseState importLicense(String fileName, byte[] content) {
        if (properties.getMode() != LicenseMode.OFFLINE) {
            throw new LicenseFileException("当前授权模式不是离线模式");
        }
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".lic")) {
            throw new LicenseFileException("License 文件必须使用 .lic 扩展名");
        }

        LicenseInfo candidate = codec.read(content);
        LicenseState candidateState = licenseManager.validateCandidate(candidate);
        if (candidateState != LicenseState.ACTIVE) {
            throw new LicenseFileException("License 校验失败：" + candidateState);
        }

        store.replace(properties.getLicenseFile(), content);
        return licenseManager.refresh();
    }

    public LicenseState refresh() {
        return licenseManager.refresh();
    }
}
