package com.faber.core.license;

import java.util.Optional;

@FunctionalInterface
public interface LicenseProvider {

    Optional<LicenseInfo> load();

    default boolean supports(LicenseMode mode) {
        return true;
    }
}
