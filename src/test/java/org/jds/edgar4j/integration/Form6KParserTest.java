package org.jds.edgar4j.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jds.edgar4j.model.Form6K;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Form6KParser.
 */
@DisplayName("Form 6-K Parser Tests")
class Form6KParserTest {

    private static final String ACCESSION = "0001111111-25-000001";

    private final Form6KParser parser = new Form6KParser();

    @Nested
    @DisplayName("Parse Valid HTML")
    class ParseValidHtml {

        @Test
        @DisplayName("should parse form type and trading symbol")
        void shouldParseFormTypeAndTradingSymbol() throws IOException {
            String html = Files.readString(Path.of("src/test/resources/form6k/sample_6k.html"));

            Form6K result = parser.parse(html, ACCESSION);

            assertThat(result).isNotNull();
            assertThat(result.getAccessionNumber()).isEqualTo(ACCESSION);
            assertThat(result.getFormType()).isEqualTo("6-K");
            assertThat(result.getTradingSymbol()).isEqualTo("NOVA");
        }

        @Test
        @DisplayName("should extract report text and exhibits")
        void shouldExtractReportTextAndExhibits() throws IOException {
            String html = Files.readString(Path.of("src/test/resources/form6k/sample_6k.html"));

            Form6K result = parser.parse(html, ACCESSION);

            assertThat(result).isNotNull();
            assertThat(result.getReportText()).contains("unaudited interim financial information");
            assertThat(result.getExhibits()).hasSize(2);
            assertThat(result.getExhibits().get(0).getExhibitNumber()).isEqualTo("99.1");
            assertThat(result.getExhibits().get(0).getDocument()).isEqualTo("ex99-1.htm");
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

