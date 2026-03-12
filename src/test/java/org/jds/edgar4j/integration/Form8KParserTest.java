package org.jds.edgar4j.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jds.edgar4j.model.Form8K;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Form8KParser.
 */
@DisplayName("Form 8-K Parser Tests")
class Form8KParserTest {

    private static final String ACCESSION = "0001234567-25-000001";

    private final Form8KParser parser = new Form8KParser();

    @Nested
    @DisplayName("Parse Valid HTML")
    class ParseValidHtml {

        @Test
        @DisplayName("should parse form type and trading symbol")
        void shouldParseFormTypeAndTradingSymbol() throws IOException {
            String html = Files.readString(Path.of("src/test/resources/form8k/sample_8k.html"));

            Form8K result = parser.parse(html, ACCESSION);

            assertThat(result).isNotNull();
            assertThat(result.getAccessionNumber()).isEqualTo(ACCESSION);
            assertThat(result.getFormType()).isEqualTo("8-K");
            assertThat(result.getTradingSymbol()).isEqualTo("ACME");
        }

        @Test
        @DisplayName("should extract item sections and ignore TOC entries")
        void shouldExtractItemSections() throws IOException {
            String html = Files.readString(Path.of("src/test/resources/form8k/sample_8k.html"));

            Form8K result = parser.parse(html, ACCESSION);

            assertThat(result).isNotNull();
            assertThat(result.getItemSections()).isNotNull();
            assertThat(result.getItemSections()).extracting(Form8K.ItemSection::getItemNumber)
                    .containsExactly("2.02", "9.01");

            Form8K.ItemSection item202 = result.getItemSections().get(0);
            assertThat(item202.getTitle()).containsIgnoringCase("Results of Operations");
            assertThat(item202.getContent()).contains("press release");
        }

        @Test
        @DisplayName("should extract exhibit index entries when present")
        void shouldExtractExhibits() throws IOException {
            String html = Files.readString(Path.of("src/test/resources/form8k/sample_8k.html"));

            Form8K result = parser.parse(html, ACCESSION);

            assertThat(result).isNotNull();
            assertThat(result.getExhibits()).isNotNull();
            assertThat(result.getExhibits()).hasSize(1);
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

