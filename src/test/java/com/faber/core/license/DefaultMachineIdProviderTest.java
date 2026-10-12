package com.faber.core.license;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMachineIdProviderTest {

    @Test
    void prefersMachineIdFile(@TempDir Path tempDir) throws Exception {
        Path machineId = tempDir.resolve("machine-id");
        Files.writeString(machineId, " machine-id-from-host \n");
        DefaultMachineIdProvider provider = new DefaultMachineIdProvider(machineId, () -> "host", "amd64");

        assertEquals(sha256("machine-id-from-host"), provider.getMachineId());
        assertEquals(provider.getMachineId(), provider.getMachineId());
    }

    @Test
    void fallsBackToHostnameAndArchitecture(@TempDir Path tempDir) throws Exception {
        DefaultMachineIdProvider provider = new DefaultMachineIdProvider(
                tempDir.resolve("missing"), () -> " host ", " amd64 ");

        String result = provider.getMachineId();
        assertEquals(sha256("hostname=host\narch=amd64"), result);
        assertTrue(result.matches("[0-9a-f]{64}"));
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte item : digest) {
            result.append(String.format("%02x", item));
        }
        return result.toString();
    }
}
