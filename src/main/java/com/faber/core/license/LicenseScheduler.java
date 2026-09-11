package com.faber.core.license;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LicenseScheduler {

    private final LicenseProperties properties;
    private final LicenseManager licenseManager;

    public LicenseScheduler(LicenseProperties properties, LicenseManager licenseManager) {
        this.properties = properties;
        this.licenseManager = licenseManager;
    }

    @Scheduled(fixedDelayString = "#{${fa.license.refresh-interval-seconds:3600} * 1000}")
    public void refresh() {
        if (properties.isEnabled() && properties.getMode() == LicenseMode.ONLINE) {
            licenseManager.refresh();
        }
    }
}
