package com.faber.core.crypto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public record CryptoEnvelope(Integer v, String kid, String challengeId, String purpose,
                             String encryptedKey, String iv, String ciphertext) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("加密信封包含未知字段");
    }
}
