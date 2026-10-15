package com.faber.core.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.Base64;

public final class RequestCrypto {

    public static final int MAX_PLAINTEXT_BYTES = 4096;
    private static final ObjectMapper JSON = new ObjectMapper();

    private RequestCrypto() {}

    public static byte[] aad(CryptoEnvelope envelope, String method, String path) throws IOException {
        return JSON.writeValueAsBytes(Arrays.asList(envelope.v(), envelope.kid(), envelope.challengeId(),
                envelope.purpose(), method, path));
    }

    public static byte[] decrypt(CryptoEnvelope envelope, PrivateKey privateKey, String method, String path)
            throws GeneralSecurityException, IOException {
        if (envelope == null || !Integer.valueOf(1).equals(envelope.v())
                || envelope.kid() == null || !envelope.kid().matches("[A-Za-z0-9._-]{1,64}")
                || envelope.purpose() == null || !envelope.purpose().matches("[A-Z_]{1,64}")
                || (envelope.challengeId() != null && !envelope.challengeId().matches("[A-Za-z0-9_-]{43}"))) {
            throw new GeneralSecurityException("无效加密信封");
        }
        byte[] encryptedKey = decode(envelope.encryptedKey(), 256, 256);
        byte[] iv = decode(envelope.iv(), 12, 12);
        byte[] ciphertext = decode(envelope.ciphertext(), 16, MAX_PLAINTEXT_BYTES + 16);
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.DECRYPT_MODE, privateKey,
                new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        byte[] key = rsa.doFinal(encryptedKey);
        try {
            if (key.length != 32) throw new GeneralSecurityException("无效加密信封");
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            aes.updateAAD(aad(envelope, method, path));
            return aes.doFinal(ciphertext);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static byte[] decode(String value, int minBytes, int maxBytes) throws GeneralSecurityException {
        if (value == null || value.length() > 4 * ((maxBytes + 2) / 3)) {
            throw new GeneralSecurityException("无效加密信封");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length < minBytes || decoded.length > maxBytes
                    || !Base64.getEncoder().encodeToString(decoded).equals(value)) {
                throw new GeneralSecurityException("无效加密信封");
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("无效加密信封");
        }
    }
}
