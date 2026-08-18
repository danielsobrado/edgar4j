package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jds.edgar4j.service.WorkerTokenService.IssuedToken;
import org.junit.jupiter.api.Test;

class WorkerTokenServiceImplTest {

    private final WorkerTokenServiceImpl tokenService = new WorkerTokenServiceImpl();

    @Test
    void issuedTokensAreRandomAndPersistableOnlyAsHashes() {
        IssuedToken first = tokenService.issue();
        IssuedToken second = tokenService.issue();

        assertNotNull(first.value());
        assertNotNull(first.hash());
        assertNotEquals(first.value(), first.hash());
        assertNotEquals(first.value(), second.value());
        assertNotEquals(first.hash(), second.hash());
        assertTrue(tokenService.matches(first.value(), first.hash()));
        assertFalse(tokenService.matches(second.value(), first.hash()));
    }

    @Test
    void malformedStoredHashNeverAuthenticates() {
        IssuedToken token = tokenService.issue();

        assertFalse(tokenService.matches(token.value(), "not-hex"));
        assertFalse(tokenService.matches(null, token.hash()));
    }
}
