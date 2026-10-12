package com.faber.core.license;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;
import java.util.HexFormat;

@Component
public class DefaultMachineIdProvider implements MachineIdProvider {

    private static final Path MACHINE_ID_PATH = Path.of("/etc/machine-id");

    private final Path machineIdPath;
    private final Supplier<String> hostnameSupplier;
    private final String architecture;

    public DefaultMachineIdProvider() {
        this(MACHINE_ID_PATH, DefaultMachineIdProvider::resolveHostname, System.getProperty("os.arch"));
    }

    DefaultMachineIdProvider(Path machineIdPath, Supplier<String> hostnameSupplier, String architecture) {
        this.machineIdPath = machineIdPath;
        this.hostnameSupplier = hostnameSupplier;
        this.architecture = architecture;
    }

    @Override
    public String getMachineId() {
        String machineId = readMachineId();
        String source = hasText(machineId)
                ? machineId.trim()
                : "hostname=" + valueOrUnknown(hostnameSupplier.get()) + "\narch=" + valueOrUnknown(architecture);
        return sha256(source);
    }

    private String readMachineId() {
        try {
            return Files.readString(machineIdPath, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException ignored) {
            return null;
        }
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String valueOrUnknown(String value) {
        return hasText(value) ? value.trim() : "unknown";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 不支持 SHA-256", e);
        }
    }
}
