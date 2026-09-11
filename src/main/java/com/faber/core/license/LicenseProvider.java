package com.faber.core.license;

import java.util.Optional;

@FunctionalInterface
public interface LicenseProvider {

    Optional<LicenseInfo> load();
}
