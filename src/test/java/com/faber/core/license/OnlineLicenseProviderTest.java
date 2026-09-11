package com.faber.core.license;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnlineLicenseProviderTest {

    @Test
    void requestsConfiguredOnlineLicense() {
        LicenseProperties properties = properties();
        OnlineLicenseClient client = mock(OnlineLicenseClient.class);
        OnlineLicenseCache cache = mock(OnlineLicenseCache.class);
        LicenseInfo license = license();
        OnlineLicenseResponse response = new OnlineLicenseResponse();
        response.setCode("OK");
        response.setLicense(license);
        when(client.validate(any(), any())).thenReturn(response);

        OnlineLicenseProvider provider = new OnlineLicenseProvider(properties, () -> "machine-1", client, cache);

        assertEquals(Optional.of(license), provider.load());
        provider.onAccepted(license);
        verify(client).validate("https://license.example.com", new OnlineLicenseRequest(
                "key-1", "fa-admin", "machine-1", "1.0.0"));
        verify(cache).save(license);
    }

    @Test
    void fallsBackToCacheWhenCenterIsUnavailable() {
        LicenseProperties properties = properties();
        OnlineLicenseClient client = mock(OnlineLicenseClient.class);
        OnlineLicenseCache cache = mock(OnlineLicenseCache.class);
        LicenseInfo license = license();
        when(client.validate(any(), any())).thenThrow(new IllegalStateException("timeout"));
        when(cache.load()).thenReturn(Optional.of(license));

        OnlineLicenseProvider provider = new OnlineLicenseProvider(properties, () -> "machine-1", client, cache);

        assertEquals(Optional.of(license), provider.load());
    }

    @Test
    void reportsUnavailableWhenCenterAndCacheBothFail() {
        LicenseProperties properties = properties();
        OnlineLicenseClient client = mock(OnlineLicenseClient.class);
        OnlineLicenseCache cache = mock(OnlineLicenseCache.class);
        when(client.validate(any(), any())).thenThrow(new IllegalStateException("timeout"));

        OnlineLicenseProvider provider = new OnlineLicenseProvider(properties, () -> "machine-1", client, cache);

        assertThrows(OnlineLicenseException.class, provider::load);
    }

    @Test
    void rejectsNonSuccessResponse() {
        LicenseProperties properties = properties();
        OnlineLicenseClient client = mock(OnlineLicenseClient.class);
        OnlineLicenseResponse response = new OnlineLicenseResponse();
        response.setCode("INVALID_LICENSE");
        when(client.validate(any(), any())).thenReturn(response);

        OnlineLicenseProvider provider = new OnlineLicenseProvider(properties, () -> "machine-1", client,
                mock(OnlineLicenseCache.class));

        assertThrows(LicenseFileException.class, provider::load);
    }

    private static LicenseProperties properties() {
        LicenseProperties properties = new LicenseProperties();
        properties.setMode(LicenseMode.ONLINE);
        properties.setServerUrl("https://license.example.com");
        properties.setLicenseKey("key-1");
        properties.setProduct("fa-admin");
        properties.setClientVersion("1.0.0");
        return properties;
    }

    private static LicenseInfo license() {
        return LicenseInfo.builder()
                .licenseId("license-1")
                .product("fa-admin")
                .machineId("machine-1")
                .mode(LicenseMode.ONLINE)
                .issuedAt(Instant.parse("2026-09-01T00:00:00Z"))
                .expireAt(Instant.parse("2026-09-14T00:00:00Z"))
                .status(LicenseState.ACTIVE)
                .signature("signature")
                .build();
    }
}
