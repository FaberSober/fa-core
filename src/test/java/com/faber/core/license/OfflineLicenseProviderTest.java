package com.faber.core.license;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OfflineLicenseProviderTest {

    @Test
    void returnsEmptyWhenFileIsNotConfigured(@TempDir Path tempDir) {
        LicenseProperties properties = new LicenseProperties();
        properties.setMode(LicenseMode.OFFLINE);
        properties.setLicenseFile(tempDir.resolve("missing.lic").toString());

        Optional<LicenseInfo> result = new OfflineLicenseProvider(properties, new LicenseFileCodec()).load();

        assertTrue(result.isEmpty());
    }

    @Test
    void loadsConfiguredLicense(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("license.lic");
        Files.write(path, fileBytes());
        LicenseProperties properties = new LicenseProperties();
        properties.setMode(LicenseMode.OFFLINE);
        properties.setLicenseFile(path.toString());

        Optional<LicenseInfo> result = new OfflineLicenseProvider(properties, new LicenseFileCodec()).load();

        assertTrue(result.isPresent());
        assertEquals("license-1", result.get().getLicenseId());
    }

    @Test
    void rejectsNonLicAndMalformedFiles(@TempDir Path tempDir) throws Exception {
        LicenseProperties properties = new LicenseProperties();
        properties.setMode(LicenseMode.OFFLINE);
        properties.setLicenseFile(tempDir.resolve("license.txt").toString());
        OfflineLicenseProvider provider = new OfflineLicenseProvider(properties, new LicenseFileCodec());
        assertThrows(LicenseFileException.class, provider::load);

        Path malformed = tempDir.resolve("malformed.lic");
        Files.writeString(malformed, "not-json");
        properties.setLicenseFile(malformed.toString());
        assertThrows(LicenseFileException.class, provider::load);
    }

    @Test
    void activatesSignedOfflineLicense(@TempDir Path tempDir) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String publicKey = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";

        LicenseInfo license = LicenseInfo.builder()
                .licenseId("license-1")
                .product("fa-admin")
                .machineId("machine-1")
                .mode(LicenseMode.OFFLINE)
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expireAt(Instant.parse("2027-01-01T00:00:00Z"))
                .status(LicenseState.ACTIVE)
                .signature(sign(licenseWithoutSignature().canonicalPayload(), keyPair))
                .build();
        Path path = tempDir.resolve("license.lic");
        Files.write(path, fileBytes(license));

        LicenseProperties properties = new LicenseProperties();
        properties.setMode(LicenseMode.OFFLINE);
        properties.setPublicKey(publicKey);
        properties.setLicenseFile(path.toString());
        OfflineLicenseProvider offlineProvider = new OfflineLicenseProvider(properties, new LicenseFileCodec());
        ObjectProvider<LicenseProvider> providers = mock(ObjectProvider.class);
        when(providers.orderedStream()).thenReturn(Stream.of(offlineProvider));
        LicenseManager manager = new LicenseManager(properties, () -> "machine-1",
                new RsaLicenseVerifier(publicKey), providers,
                Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC));

        manager.initialize();
        assertEquals(LicenseState.ACTIVE, manager.getState());
    }

    private static byte[] fileBytes() {
        LicenseInfo license = licenseWithoutSignature();
        license.setSignature("signature");
        return fileBytes(license);
    }

    private static LicenseInfo licenseWithoutSignature() {
        return LicenseInfo.builder()
                .licenseId("license-1")
                .product("fa-admin")
                .machineId("machine-1")
                .mode(LicenseMode.OFFLINE)
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expireAt(Instant.parse("2027-01-01T00:00:00Z"))
                .status(LicenseState.ACTIVE)
                .signature(null)
                .build();
    }

    private static byte[] fileBytes(LicenseInfo license) {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("payload", JSON.parseObject(license.canonicalPayload()));
        root.put("signature", license.getSignature());
        return root.toJSONString().getBytes(StandardCharsets.UTF_8);
    }

    private static String sign(String payload, KeyPair keyPair) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }
}
