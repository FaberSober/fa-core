package com.faber.core.license;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OnlineLicenseCacheTest {

    @Test
    void savesAndLoadsSignedLicense(@TempDir Path tempDir) throws Exception {
        LicenseProperties properties = new LicenseProperties();
        properties.setCacheFile(tempDir.resolve("license-cache.lic").toString());
        LicenseInfo license = LicenseInfo.builder()
                .licenseId("license-1")
                .product("fa-admin")
                .machineId("machine-1")
                .mode(LicenseMode.ONLINE)
                .issuedAt(Instant.parse("2026-09-01T00:00:00Z"))
                .expireAt(Instant.parse("2026-09-14T00:00:00Z"))
                .status(LicenseState.ACTIVE)
                .signature("signature")
                .build();

        OnlineLicenseCache cache = new OnlineLicenseCache(properties, new LicenseFileCodec(),
                new OfflineLicenseStore());
        cache.save(license);

        assertEquals(Optional.of(license), cache.load());
        assertEquals(license.canonicalPayload(), new LicenseFileCodec()
                .read(Files.readAllBytes(Path.of(properties.getCacheFile())))
                .canonicalPayload());
    }

    @Test
    void ignoresCorruptedCache(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("license-cache.lic");
        Files.writeString(path, "not-json");
        LicenseProperties properties = new LicenseProperties();
        properties.setCacheFile(path.toString());

        assertEquals(Optional.empty(), new OnlineLicenseCache(properties, new LicenseFileCodec(),
                new OfflineLicenseStore()).load());
    }
}
