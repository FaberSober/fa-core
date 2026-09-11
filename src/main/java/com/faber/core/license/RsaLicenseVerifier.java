package com.faber.core.license;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class RsaLicenseVerifier implements LicenseVerifier {

    private final String publicKeyPem;

    @Autowired
    public RsaLicenseVerifier(LicenseProperties properties) {
        this(properties.getPublicKey());
    }

    RsaLicenseVerifier(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    @Override
    public boolean verify(LicenseInfo licenseInfo) {
        if (licenseInfo == null || publicKeyPem == null || publicKeyPem.isBlank()
                || licenseInfo.getSignature() == null || licenseInfo.getSignature().isBlank()) {
            return false;
        }
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(parsePublicKey(publicKeyPem));
            verifier.update(licenseInfo.canonicalPayload().getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(licenseInfo.getSignature()));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static PublicKey parsePublicKey(String pem) throws Exception {
        String encoded = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        if (encoded.isEmpty()) {
            throw new IllegalArgumentException("公钥为空");
        }
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }
}
