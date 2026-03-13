package org.jds.edgar4j.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecAccessDiagnosticsTest {

    @Test
    @DisplayName("should detect the SEC undeclared automation block page and extract its reference id")
    void shouldDetectUndeclaredAutomationBlockPage() {
        String body = """
                <html>
                  <head><title>SEC.gov | Your Request Originates from an Undeclared Automated Tool</title></head>
                  <body>
                    <h1>Your Request Originates from an Undeclared Automated Tool</h1>
                    <p>Please declare your traffic by updating your user agent.</p>
                    <p>Reference ID: 0.8f553217.1773419200.12345678</p>
                  </body>
                </html>
                """;

        assertTrue(SecAccessDiagnostics.isUndeclaredAutomationBlock(body));
        assertEquals("0.8f553217.1773419200.12345678", SecAccessDiagnostics.extractReferenceId(body));
    }

    @Test
    @DisplayName("should detect the operator-facing SEC block message in nested exceptions")
    void shouldDetectOperatorFacingBlockMessageInThrowableChain() {
        RuntimeException exception = new RuntimeException(
                "wrapper",
                new IllegalStateException(SecAccessDiagnostics.buildUndeclaredAutomationBlockMessage(
                        "https://sec.example/test",
                        "ref-123")));

        assertTrue(SecAccessDiagnostics.isUndeclaredAutomationBlock(exception));
    }
}
