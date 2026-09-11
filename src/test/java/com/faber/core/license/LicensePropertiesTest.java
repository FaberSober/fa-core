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
                .withProperty("fa.license.license-file", "/data/license.lic");

        LicenseProperties properties = new LicenseProperties();
        Binder.get(environment).bind("fa.license", Bindable.ofInstance(properties));

        assertFalse(properties.isEnabled());
        assertEquals(LicenseMode.OFFLINE, properties.getMode());
        assertEquals("https://license.example.com", properties.getServerUrl());
        assertEquals("key-1", properties.getLicenseKey());
        assertEquals("/data/license.lic", properties.getLicenseFile());
        assertTrue(new LicenseProperties().isEnabled());
    }
}
