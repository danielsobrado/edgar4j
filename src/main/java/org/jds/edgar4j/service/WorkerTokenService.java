package org.jds.edgar4j.service;

public interface WorkerTokenService {

    IssuedToken issue();

    String hash(String token);

    boolean matches(String token, String expectedHash);

    record IssuedToken(String value, String hash) {
    }
}
