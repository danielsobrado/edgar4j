package org.jds.edgar4j.service.impl;

import static org.jds.edgar4j.constants.WorkerProtocolConstants.TOKEN_BYTES;
import static org.jds.edgar4j.constants.WorkerStorageConstants.SHA256_ALGORITHM;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.jds.edgar4j.service.WorkerTokenService;
import org.springframework.stereotype.Service;

@Service
public class WorkerTokenServiceImpl implements WorkerTokenService {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public IssuedToken issue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedToken(value, hash(value));
    }

    @Override
    public String hash(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Worker token is required");
        }
        return HexFormat.of().formatHex(digest(token));
    }

    @Override
    public boolean matches(String token, String expectedHash) {
        if (token == null || token.isBlank() || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        byte[] actual = digest(token);
        byte[] expected;
        try {
            expected = HexFormat.of().parseHex(expectedHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(actual, expected);
    }

    private static byte[] digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA256_ALGORITHM);
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM does not provide SHA-256", e);
        }
    }
}
