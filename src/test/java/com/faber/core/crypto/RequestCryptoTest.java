package com.faber.core.crypto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class RequestCryptoTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static JsonNode fixture;
    private static PrivateKey privateKey;
    private static CryptoEnvelope envelope;
    private static final String PATH = "/api/base/demo/crypto/roundtrip";

    @BeforeAll
    static void loadWebCryptoFixture() throws Exception {
        try (var stream = RequestCryptoTest.class.getResourceAsStream("/crypto/web-crypto-v1.json")) {
            assertNotNull(stream);
            fixture = JSON.readTree(stream);
        }
        privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(
                Base64.getDecoder().decode(fixture.get("testOnlyPrivateKey").asText())));
        envelope = JSON.treeToValue(fixture.get("envelopes").get(0), CryptoEnvelope.class);
    }

    @Test
    void decryptsRealWebCryptoOutputWithAndWithoutChallenge() throws Exception {
        for (JsonNode value : fixture.get("envelopes")) {
            var request = JSON.treeToValue(value, CryptoEnvelope.class);
            assertEquals(fixture.get("plaintext"), JSON.readTree(RequestCrypto.decrypt(request, privateKey, "POST", PATH)));
        }
    }

    @Test
    void rejectsTamperingAndRouteSubstitution() {
        byte[] bytes = Base64.getDecoder().decode(envelope.ciphertext());
        bytes[0] ^= 1;
        var altered = new CryptoEnvelope(1, envelope.kid(), null, envelope.purpose(), envelope.encryptedKey(), envelope.iv(),
                Base64.getEncoder().encodeToString(bytes));
        assertThrows(GeneralSecurityException.class, () -> RequestCrypto.decrypt(altered, privateKey, "POST", PATH));
        assertThrows(GeneralSecurityException.class, () -> RequestCrypto.decrypt(envelope, privateKey, "GET", PATH));
        assertThrows(GeneralSecurityException.class, () -> RequestCrypto.decrypt(envelope, privateKey, "POST", PATH + "2"));
    }

    @Test
    void rejectsOversizedAndMalformedBinaryFields() {
        for (String ciphertext : new String[]{null, "!invalid!", "", Base64.getEncoder().encodeToString(new byte[4113])}) {
            var invalid = new CryptoEnvelope(1, envelope.kid(), null, envelope.purpose(), envelope.encryptedKey(), envelope.iv(), ciphertext);
            assertThrows(GeneralSecurityException.class, () -> RequestCrypto.decrypt(invalid, privateKey, "POST", PATH));
        }
        var invalid = new CryptoEnvelope(2, envelope.kid(), null, envelope.purpose(), envelope.encryptedKey(), envelope.iv(), envelope.ciphertext());
        assertThrows(GeneralSecurityException.class, () -> RequestCrypto.decrypt(invalid, privateKey, "POST", PATH));
    }

    @Test
    void rejectsAppendedPlaintextEvenWhenGlobalJacksonIgnoresUnknownProperties() {
        ObjectMapper permissive = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        var input = JSON.valueToTree(envelope);
        ((com.fasterxml.jackson.databind.node.ObjectNode) input).put("password", "override");
        assertThrows(Exception.class, () -> permissive.treeToValue(input, CryptoEnvelope.class));
    }
}
