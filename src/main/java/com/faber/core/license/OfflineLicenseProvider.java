package com.faber.core.license;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

@Component
public class OfflineLicenseProvider implements LicenseProvider {

    private final LicenseProperties properties;
    private final LicenseFileCodec codec;

    public OfflineLicenseProvider(LicenseProperties properties, LicenseFileCodec codec) {
        this.properties = properties;
        this.codec = codec;
    }

    @Override
    public boolean supports(LicenseMode mode) {
        return mode == LicenseMode.OFFLINE;
    }

    @Override
    public Optional<LicenseInfo> load() {
        String configuredPath = properties.getLicenseFile();
        if (configuredPath == null || configuredPath.isBlank()) {
            return Optional.empty();
        }

        Path path = Path.of(configuredPath).toAbsolutePath().normalize();
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".lic")) {
            throw new LicenseFileException("License 文件必须使用 .lic 扩展名");
        }
        try {
            try (InputStream input = Files.newInputStream(path)) {
                return Optional.of(codec.read(input));
            }
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new LicenseFileException("读取 License 文件失败", e);
        }
    }
}
