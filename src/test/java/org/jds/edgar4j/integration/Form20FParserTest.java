package org.jds.edgar4j.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jds.edgar4j.model.Form20F;
import org.jds.edgar4j.xbrl.XbrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit tests for Form20FParser.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Form 20-F Parser Tests")
class Form20FParserTest {

    private static final String ACCESSION = "0001234567-25-000001";

    @Autowired
    private XbrlService xbrlService;

    @Nested
    @DisplayName("Parse Valid XBRL")
    class ParseValidXbrl {

        @Test
        @DisplayName("should parse metadata and key financials")
        void shouldParseMetadataAndFinancials() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form20f/sample_20f.xml"));

            Form20FParser parser = new Form20FParser(xbrlService);
            Form20F result = parser.parse(xml, ACCESSION, "sample_20f.xml");

            assertThat(result).isNotNull();
            assertThat(result.getAccessionNumber()).isEqualTo(ACCESSION);
            assertThat(result.getFormType()).isEqualTo("20-F");
            assertThat(result.getCompanyName()).isEqualTo("ACME FOREIGN LTD");
            assertThat(result.getTradingSymbol()).isEqualTo("ACME");
            assertThat(result.getFiscalYear()).isEqualTo(2025);
            assertThat(result.getKeyFinancials()).containsKeys("Assets", "Revenues");
        }
    }

    @Nested
    @DisplayName("Handle Edge Cases")
    class HandleEdgeCases {

        @Test
        @DisplayName("should return null for empty input")
        void shouldReturnNullForEmptyInput() {
            Form20FParser parser = new Form20FParser(xbrlService);
            assertThat(parser.parse("", ACCESSION, "sample_20f.xml")).isNull();
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNullInput() {
            Form20FParser parser = new Form20FParser(xbrlService);
            assertThat(parser.parse(null, ACCESSION, "sample_20f.xml")).isNull();
        }
    }
}

