package com.faber.core.license;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fa.license")
public class LicenseProperties {

    private boolean enabled = true;
    private LicenseMode mode = LicenseMode.ONLINE;
    private String serverUrl;
    private String licenseKey;
    private String publicKey;
    private String licenseFile;
    private String product = "fa-admin";
    private String clientVersion = "unknown";
    private String cacheFile = "./config/license-cache.lic";
    private long refreshIntervalSeconds = 3600;
}
