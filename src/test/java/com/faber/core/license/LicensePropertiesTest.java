package com.faber.core.license;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicensePropertiesTest {

    @Test
    void bindsLicenseConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("fa.license.enabled", "false")
                .withProperty("fa.license.mode", "offline")
                .withProperty("fa.license.server-url", "https://license.example.com")
                .withProperty("fa.license.license-key", "key-1")
                .withProperty("fa.license.license-file", "/data/license.lic")
                .withProperty("fa.license.product", "fa-admin")
                .withProperty("fa.license.client-version", "1.0.0")
                .withProperty("fa.license.cache-file", "/data/license-cache.lic")
                .withProperty("fa.license.refresh-interval-seconds", "120");

        LicenseProperties properties = new LicenseProperties();
        Binder.get(environment).bind("fa.license", Bindable.ofInstance(properties));

        assertFalse(properties.isEnabled());
        assertEquals(LicenseMode.OFFLINE, properties.getMode());
        assertEquals("https://license.example.com", properties.getServerUrl());
        assertEquals("key-1", properties.getLicenseKey());
        assertEquals("/data/license.lic", properties.getLicenseFile());
        assertEquals("fa-admin", properties.getProduct());
        assertEquals("1.0.0", properties.getClientVersion());
        assertEquals("/data/license-cache.lic", properties.getCacheFile());
        assertEquals(120, properties.getRefreshIntervalSeconds());
        assertTrue(new LicenseProperties().isEnabled());
    }
}
