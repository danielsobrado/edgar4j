package org.jds.edgar4j.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jds.edgar4j.model.Form3;
import org.jds.edgar4j.model.Form4Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Form3Parser.
 */
@DisplayName("Form 3 Parser Tests")
class Form3ParserTest {

    private static final String ACCESSION = "0000555555-25-000001";

    private final Form3Parser parser = new Form3Parser();

    @Nested
    @DisplayName("Parse Valid XML")
    class ParseValidXml {

        @Test
        @DisplayName("should parse issuer and owner info with holdings")
        void shouldParseIssuerAndHoldings() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form3/sample_form3.xml"));

            Form3 result = parser.parse(xml, ACCESSION);

            assertThat(result).isNotNull();
            assertThat(result.getAccessionNumber()).isEqualTo(ACCESSION);
            assertThat(result.getDocumentType()).isEqualTo("3");
            assertThat(result.getIssuerName()).isEqualTo("ACME CORP");
            assertThat(result.getTradingSymbol()).isEqualTo("ACME");
            assertThat(result.getRptOwnerName()).isEqualTo("Jane Doe");
            assertThat(result.getOwnerType()).isEqualTo("Director");
            assertThat(result.getHoldings()).hasSize(2);

            Form4Transaction common = result.getHoldings().stream()
                    .filter(t -> "Common Stock".equals(t.getSecurityTitle()))
                    .findFirst()
                    .orElse(null);
            assertThat(common).isNotNull();
            assertThat(common.getSharesOwnedFollowingTransaction()).isEqualTo(10000f);
            assertThat(common.getDirectOrIndirectOwnership()).isEqualTo("D");

            Form4Transaction option = result.getHoldings().stream()
                    .filter(t -> "Stock Options".equals(t.getSecurityTitle()))
                    .findFirst()
                    .orElse(null);
            assertThat(option).isNotNull();
            assertThat(option.getUnderlyingSecurityTitle()).isEqualTo("Common Stock");
            assertThat(option.getUnderlyingSecurityShares()).isEqualTo(5000f);
        }
    }

    @Nested
    @DisplayName("Handle Edge Cases")
    class HandleEdgeCases {

        @Test
        @DisplayName("should return null for empty input")
        void shouldReturnNullForEmptyInput() {
            assertThat(parser.parse("", ACCESSION)).isNull();
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            assertThat(parser.parse(null, ACCESSION)).isNull();
        }
    }
}

