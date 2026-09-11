package com.faber.core.license;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RsaLicenseVerifierTest {

    private KeyPair keyPair;
    private LicenseInfo license;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        license = LicenseInfo.builder()
                .licenseId("license-1")
                .licenseKey("key-1")
                .product("fa-admin")
                .customer("customer-1")
                .machineId("machine-1")
                .mode(LicenseMode.OFFLINE)
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expireAt(Instant.parse("2027-01-01T00:00:00Z"))
                .status(LicenseState.ACTIVE)
                .features(Set.of("ai"))
                .build();
        license.setSignature(sign(license.canonicalPayload()));
    }

    @Test
    void verifiesValidSignature() {
        assertTrue(new RsaLicenseVerifier(publicKeyPem()).verify(license));
    }

    @Test
    void rejectsPayloadTampering() {
        license.setProduct("tampered");

        assertFalse(new RsaLicenseVerifier(publicKeyPem()).verify(license));
    }

    @Test
    void rejectsMalformedKeyAndSignature() {
        assertFalse(new RsaLicenseVerifier("not-a-public-key").verify(license));
        license.setSignature("not-base64");
        assertFalse(new RsaLicenseVerifier(publicKeyPem()).verify(license));
    }

    private String sign(String payload) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private String publicKeyPem() {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
