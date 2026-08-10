package com.aitrainercrm.platform.security.token;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Generates opaque, high-entropy tokens (refresh tokens, password reset
 * links, email verification links) and hashes them for storage. Every
 * such token in this platform follows the same rule: the raw value is
 * handed to the client/emailed once and never persisted; only its SHA-256
 * hash lives in the database, so a database breach alone can't be used to
 * forge a valid session or reset link.
 */
@Component
public class SecureTokenService {

    private static final int TOKEN_BYTES = 32; // 256 bits of entropy
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm - this can never actually happen.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
