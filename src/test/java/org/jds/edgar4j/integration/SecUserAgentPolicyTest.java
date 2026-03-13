package org.jds.edgar4j.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecUserAgentPolicyTest {

    @Test
    @DisplayName("normalizeAndValidate should accept a real SEC contact string")
    void normalizeAndValidateShouldAcceptARealSecContactString() {
        String normalized = SecUserAgentPolicy.normalizeAndValidate("  My Company   sec-ops@mycompany.com  ");

        assertEquals("My Company sec-ops@mycompany.com", normalized);
    }

    @Test
    @DisplayName("isValid should reject placeholder and noreply email domains")
    void isValidShouldRejectPlaceholderAndNoreplyEmailDomains() {
        assertFalse(SecUserAgentPolicy.isValid("Edgar4j/1.0 (contact@example.com)"));
        assertFalse(SecUserAgentPolicy.isValid("Edgar4j 35833752+danielsobrado@users.noreply.github.com"));
    }

    @Test
    @DisplayName("normalizeAndValidate should reject strings without a real contact email")
    void normalizeAndValidateShouldRejectStringsWithoutARealContactEmail() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SecUserAgentPolicy.normalizeAndValidate("Edgar4j/1.0"));

        assertTrue(exception.getMessage().contains("real contact email"));
    }
}
