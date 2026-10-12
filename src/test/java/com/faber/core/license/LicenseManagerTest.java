package com.faber.core.license;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LicenseManagerTest {

    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");

    @Test
    void reportsUnconfiguredWhenProviderHasNoLicense() {
        LicenseManager manager = manager(Optional.empty(), NOW);

        assertEquals(LicenseState.UNCONFIGURED, manager.getState());
        assertFalse(manager.isValid());
    }

    @Test
    void validatesActiveLicenseAndFeatures() {
        LicenseInfo license = license(LicenseState.ACTIVE, NOW.plusSeconds(3600));
        license.setFeatures(java.util.Set.of("ai"));
        LicenseManager manager = manager(Optional.of(license), NOW);

        assertEquals(LicenseState.ACTIVE, manager.getState());
        assertTrue(manager.isValid());
        assertTrue(manager.hasFeature("ai"));
        assertFalse(manager.hasFeature("gis"));
    }

    @Test
    void mapsExpiredDisabledAndMachineMismatch() {
        assertEquals(LicenseState.EXPIRED,
                manager(Optional.of(license(LicenseState.ACTIVE, NOW)), NOW).getState());
        assertEquals(LicenseState.DISABLED,
                manager(Optional.of(license(LicenseState.DISABLED, NOW.plusSeconds(3600))), NOW).getState());

        LicenseInfo mismatch = license(LicenseState.ACTIVE, NOW.plusSeconds(3600));
        mismatch.setMachineId("other-machine");
        assertEquals(LicenseState.MACHINE_MISMATCH, manager(Optional.of(mismatch), NOW).getState());
    }

    @Test
    void entersGraceAndThenBlocksExpiredOnlineLicense() {
        LicenseProperties properties = new LicenseProperties();
        properties.setMode(LicenseMode.ONLINE);
        LicenseInfo license = license(LicenseMode.ONLINE, LicenseState.ACTIVE, NOW.minusSeconds(3600));
        license.setGracePeriod(3600L);

        assertEquals(LicenseState.GRACE,
                manager(properties, Optional.of(license), NOW).getState());
        assertEquals(LicenseState.BLOCKED,
                manager(properties, Optional.of(license), NOW.plusSeconds(3601)).getState());
    }

    @Test
    void keepsCurrentLicenseWhenOnlineRefreshTemporarilyFails() {
        LicenseProperties properties = new LicenseProperties();
        properties.setMode(LicenseMode.ONLINE);
        LicenseInfo license = license(LicenseMode.ONLINE, LicenseState.ACTIVE, NOW.plusSeconds(3600));
        ObjectProvider<LicenseProvider> providers = mock(ObjectProvider.class);
        LicenseProvider provider = mock(LicenseProvider.class);
        when(providers.orderedStream()).thenAnswer(invocation -> Stream.of(provider));
        when(provider.supports(LicenseMode.ONLINE)).thenReturn(true);
        when(provider.load()).thenReturn(Optional.of(license))
                .thenThrow(new OnlineLicenseException("unavailable", new IllegalStateException()));

        LicenseManager manager = new LicenseManager(properties, () -> "machine-1", licenseInfo -> true,
                providers, fixedClock(NOW));

        assertEquals(LicenseState.ACTIVE, manager.refresh());
        assertEquals(LicenseState.ACTIVE, manager.refresh());
        assertEquals(license, manager.getLicenseInfo());
    }

    @Test
    void rejectsLicenseWithDifferentMode() {
        LicenseProperties properties = new LicenseProperties();
        properties.setMode(LicenseMode.ONLINE);

        assertEquals(LicenseState.INVALID,
                manager(properties, Optional.of(license(LicenseState.ACTIVE, NOW.plusSeconds(3600))), NOW).getState());
    }

    @Test
    void bypassesValidationWhenDisabled() {
        LicenseProperties properties = new LicenseProperties();
        properties.setEnabled(false);
        LicenseManager manager = manager(properties, Optional.empty(), NOW);

        assertEquals(LicenseState.BYPASSED, manager.getState());
        assertTrue(manager.isValid());
        assertTrue(manager.hasFeature("anything"));
    }

    @Test
    void convertsProviderFailureToInvalid() {
        ObjectProvider<LicenseProvider> providers = mock(ObjectProvider.class);
        LicenseProvider provider = () -> {
            throw new IllegalStateException("provider unavailable");
        };
        when(providers.orderedStream()).thenReturn(Stream.of(provider));
        LicenseManager manager = new LicenseManager(
                new LicenseProperties(), () -> "machine-1", licenseInfo -> true, providers, fixedClock(NOW));

        assertEquals(LicenseState.INVALID, manager.refresh());
        assertFalse(manager.isValid());
    }

    private static LicenseManager manager(Optional<LicenseInfo> license, Instant now) {
        LicenseProperties properties = new LicenseProperties();
        properties.setMode(LicenseMode.OFFLINE);
        return manager(properties, license, now);
    }

    private static LicenseManager manager(LicenseProperties properties,
                                          Optional<LicenseInfo> license,
                                          Instant now) {
        ObjectProvider<LicenseProvider> providers = mock(ObjectProvider.class);
        LicenseProvider provider = () -> license;
        when(providers.orderedStream()).thenReturn(Stream.of(provider));
        LicenseManager manager = new LicenseManager(properties, () -> "machine-1", licenseInfo -> true,
                providers, fixedClock(now));
        manager.refresh();
        return manager;
    }

    private static LicenseInfo license(LicenseState state, Instant expireAt) {
        return license(LicenseMode.OFFLINE, state, expireAt);
    }

    private static LicenseInfo license(LicenseMode mode, LicenseState state, Instant expireAt) {
        return LicenseInfo.builder()
                .licenseId("license-1")
                .product("fa-admin")
                .machineId("machine-1")
                .mode(mode)
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expireAt(expireAt)
                .status(state)
                .signature("signature")
                .build();
    }

    private static Clock fixedClock(Instant now) {
        return Clock.fixed(now, ZoneOffset.UTC);
    }
}
