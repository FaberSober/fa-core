package com.faber.core.license;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class OnlineLicenseCache {

    private final LicenseProperties properties;
    private final LicenseFileCodec codec;
    private final OfflineLicenseStore store;

    public OnlineLicenseCache(LicenseProperties properties,
                              LicenseFileCodec codec,
                              OfflineLicenseStore store) {
        this.properties = properties;
        this.codec = codec;
        this.store = store;
    }

    public Optional<LicenseInfo> load() {
        String configuredPath = properties.getCacheFile();
        if (configuredPath == null || configuredPath.isBlank()) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(configuredPath).toAbsolutePath().normalize();
            try (InputStream input = Files.newInputStream(path)) {
                return Optional.of(codec.read(input));
            }
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public void save(LicenseInfo licenseInfo) {
        store.replace(properties.getCacheFile(), codec.write(licenseInfo));
    }
}
