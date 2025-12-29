package org.jds.edgar4j.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jds.edgar4j.model.Form5;
import org.jds.edgar4j.model.Form4Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Form5Parser.
 */
@DisplayName("Form 5 Parser Tests")
class Form5ParserTest {

    private static final String ACCESSION = "0000777777-25-000001";

    private final Form5Parser parser = new Form5Parser();

    @Nested
    @DisplayName("Parse Valid XML")
    class ParseValidXml {

        @Test
        @DisplayName("should parse issuer info, transactions, and holdings")
        void shouldParseTransactionsAndHoldings() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form5/sample_form5.xml"));

            Form5 result = parser.parse(xml, ACCESSION);

            assertThat(result).isNotNull();
            assertThat(result.getAccessionNumber()).isEqualTo(ACCESSION);
            assertThat(result.getDocumentType()).isEqualTo("5");
            assertThat(result.getIssuerName()).isEqualTo("NOVA LTD");
            assertThat(result.getTradingSymbol()).isEqualTo("NOVA");
            assertThat(result.getOwnerType()).isEqualTo("Officer");

            assertThat(result.getTransactions()).hasSize(2);
            assertThat(result.getHoldings()).hasSize(1);

            Form4Transaction purchase = result.getTransactions().stream()
                    .filter(t -> "P".equals(t.getTransactionCode()))
                    .findFirst()
                    .orElse(null);
            assertThat(purchase).isNotNull();
            assertThat(purchase.getTransactionShares()).isEqualTo(1500f);
            assertThat(purchase.getTransactionPricePerShare()).isEqualTo(12.5f);

            Form4Transaction holding = result.getHoldings().get(0);
            assertThat(holding.getSharesOwnedFollowingTransaction()).isEqualTo(2000f);
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

