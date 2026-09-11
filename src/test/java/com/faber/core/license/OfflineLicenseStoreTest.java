package com.faber.core.license;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class OfflineLicenseStoreTest {

    @Test
    void atomicallyReplacesConfiguredFile(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("license.lic");
        Files.writeString(target, "old", StandardCharsets.UTF_8);

        new OfflineLicenseStore().replace(target.toString(), "new".getBytes(StandardCharsets.UTF_8));

        assertArrayEquals("new".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(target));
    }
}
