package com.faber.core.license;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Component
public class LicenseManager {

    private final LicenseProperties properties;
    private final MachineIdProvider machineIdProvider;
    private final LicenseVerifier verifier;
    private final ObjectProvider<LicenseProvider> provider;
    private final Clock clock;

    private volatile LicenseInfo licenseInfo;
    private volatile boolean providerFailed;

    @Autowired
    public LicenseManager(LicenseProperties properties,
                          MachineIdProvider machineIdProvider,
                          LicenseVerifier verifier,
                          ObjectProvider<LicenseProvider> provider) {
        this(properties, machineIdProvider, verifier, provider, Clock.systemUTC());
    }

    LicenseManager(LicenseProperties properties,
                   MachineIdProvider machineIdProvider,
                   LicenseVerifier verifier,
                   ObjectProvider<LicenseProvider> provider,
                   Clock clock) {
        this.properties = properties;
        this.machineIdProvider = machineIdProvider;
        this.verifier = verifier;
        this.provider = provider;
        this.clock = clock;
    }

    public LicenseInfo getLicenseInfo() {
        return licenseInfo;
    }

    public LicenseState getState() {
        if (providerFailed) {
            return properties.isEnabled() ? LicenseState.INVALID : LicenseState.BYPASSED;
        }
        try {
            return validate(licenseInfo);
        } catch (Exception ignored) {
            return LicenseState.INVALID;
        }
    }

    public boolean isValid() {
        LicenseState state = getState();
        return state == LicenseState.ACTIVE || state == LicenseState.BYPASSED;
    }

    public synchronized LicenseState refresh() {
        providerFailed = false;
        try {
            LicenseProvider currentProvider = provider.getIfAvailable();
            if (currentProvider == null) {
                return getState();
            }
            Optional<LicenseInfo> loaded = currentProvider.load();
            licenseInfo = loaded == null ? null : loaded.orElse(null);
        } catch (Exception ignored) {
            licenseInfo = null;
            providerFailed = true;
        }
        return getState();
    }

    public boolean hasFeature(String feature) {
        LicenseState state = getState();
        if (state == LicenseState.BYPASSED) {
            return true;
        }
        return state == LicenseState.ACTIVE
                && feature != null
                && licenseInfo != null
                && licenseInfo.getFeatures() != null
                && licenseInfo.getFeatures().contains(feature);
    }

    private LicenseState validate(LicenseInfo license) {
        if (!properties.isEnabled()) {
            return LicenseState.BYPASSED;
        }
        if (license == null) {
            return LicenseState.UNCONFIGURED;
        }
        boolean verified;
        try {
            verified = verifier.verify(license);
        } catch (Exception ignored) {
            verified = false;
        }
        if (!hasRequiredFields(license) || !verified) {
            return LicenseState.INVALID;
        }
        try {
            if (!machineIdProvider.getMachineId().equals(license.getMachineId())) {
                return LicenseState.MACHINE_MISMATCH;
            }
        } catch (Exception ignored) {
            return LicenseState.INVALID;
        }
        if (license.getStatus() == LicenseState.DISABLED) {
            return LicenseState.DISABLED;
        }
        if (license.getStatus() == LicenseState.EXPIRED) {
            return LicenseState.EXPIRED;
        }
        if (license.getStatus() != LicenseState.ACTIVE) {
            return license.getStatus() == LicenseState.TIME_ANOMALY
                    ? LicenseState.TIME_ANOMALY
                    : LicenseState.INVALID;
        }
        if (license.getIssuedAt().isAfter(license.getExpireAt())) {
            return LicenseState.TIME_ANOMALY;
        }
        if (!license.getExpireAt().isAfter(Instant.now(clock))) {
            return LicenseState.EXPIRED;
        }
        return LicenseState.ACTIVE;
    }

    private static boolean hasRequiredFields(LicenseInfo license) {
        return hasText(license.getLicenseId())
                && hasText(license.getProduct())
                && hasText(license.getMachineId())
                && license.getMode() != null
                && license.getIssuedAt() != null
                && license.getExpireAt() != null
                && license.getStatus() != null
                && hasText(license.getSignature());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
