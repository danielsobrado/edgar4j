package org.jds.edgar4j.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.model.Form13FHolding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Form13FParser.
 */
@DisplayName("Form 13F Parser Tests")
class Form13FParserTest {

    private Form13FParser parser;

    @BeforeEach
    void setUp() {
        parser = new Form13FParser();
    }

    @Nested
    @DisplayName("Parse Valid XML")
    class ParseValidXml {

        @Test
        @DisplayName("should parse sample information table with multiple holdings")
        void shouldParseSampleInformationTable() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13f/sample_infotable.xml"));
            String accessionNumber = "0001234567-24-000001";

            Form13F result = parser.parseInformationTable(xml, accessionNumber);

            assertThat(result).isNotNull();
            assertThat(result.getAccessionNumber()).isEqualTo(accessionNumber);
            assertThat(result.getHoldings()).hasSize(4);
            assertThat(result.getHoldingsCount()).isEqualTo(4);
            assertThat(result.getTotalValue()).isEqualTo(4800000L); // 1.5M + 2M + 800K + 500K
        }

        @Test
        @DisplayName("should parse Apple holding correctly")
        void shouldParseAppleHolding() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13f/sample_infotable.xml"));

            Form13F result = parser.parseInformationTable(xml, "0001234567-24-000001");

            Form13FHolding apple = result.getHoldings().stream()
                    .filter(h -> "APPLE INC".equals(h.getNameOfIssuer()))
                    .findFirst()
                    .orElse(null);

            assertThat(apple).isNotNull();
            assertThat(apple.getTitleOfClass()).isEqualTo("COM");
            assertThat(apple.getCusip()).isEqualTo("037833100");
            assertThat(apple.getValue()).isEqualTo(1500000L);
            assertThat(apple.getSharesOrPrincipalAmount()).isEqualTo(8500L);
            assertThat(apple.getSharesOrPrincipalAmountType()).isEqualTo("SH");
            assertThat(apple.getInvestmentDiscretion()).isEqualTo("SOLE");
            assertThat(apple.getVotingAuthoritySole()).isEqualTo(8500L);
            assertThat(apple.getVotingAuthorityShared()).isEqualTo(0L);
            assertThat(apple.getVotingAuthorityNone()).isEqualTo(0L);
        }

        @Test
        @DisplayName("should parse holding with PUT/CALL indicator")
        void shouldParseHoldingWithPutCall() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13f/sample_infotable.xml"));

            Form13F result = parser.parseInformationTable(xml, "0001234567-24-000001");

            Form13FHolding amazon = result.getHoldings().stream()
                    .filter(h -> "AMAZON COM INC".equals(h.getNameOfIssuer()))
                    .findFirst()
                    .orElse(null);

            assertThat(amazon).isNotNull();
            assertThat(amazon.getPutCall()).isEqualTo("CALL");
            assertThat(amazon.getInvestmentDiscretion()).isEqualTo("DFND");
        }

        @Test
        @DisplayName("should parse holding with other manager reference")
        void shouldParseHoldingWithOtherManager() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13f/sample_infotable.xml"));

            Form13F result = parser.parseInformationTable(xml, "0001234567-24-000001");

            Form13FHolding tesla = result.getHoldings().stream()
                    .filter(h -> "TESLA INC".equals(h.getNameOfIssuer()))
                    .findFirst()
                    .orElse(null);

            assertThat(tesla).isNotNull();
            assertThat(tesla.getOtherManager()).isEqualTo("2");
            assertThat(tesla.getInvestmentDiscretion()).isEqualTo("OTR");
            assertThat(tesla.getVotingAuthoritySole()).isEqualTo(1000L);
            assertThat(tesla.getVotingAuthorityShared()).isEqualTo(500L);
            assertThat(tesla.getVotingAuthorityNone()).isEqualTo(500L);
        }

        @Test
        @DisplayName("should parse minimal valid XML")
        void shouldParseMinimalXml() {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <informationTable xmlns="http://www.sec.gov/edgar/document/thirteenf/informationtable">
                    <infoTable>
                        <nameOfIssuer>TEST CORP</nameOfIssuer>
                        <titleOfClass>COM</titleOfClass>
                        <cusip>123456789</cusip>
                        <value>100000</value>
                        <shrsOrPrnAmt>
                            <sshPrnamt>1000</sshPrnamt>
                            <sshPrnamtType>SH</sshPrnamtType>
                        </shrsOrPrnAmt>
                        <investmentDiscretion>SOLE</investmentDiscretion>
                        <votingAuthority>
                            <Sole>1000</Sole>
                            <Shared>0</Shared>
                            <None>0</None>
                        </votingAuthority>
                    </infoTable>
                </informationTable>
                """;

            Form13F result = parser.parseInformationTable(xml, "0001234567-24-000002");

            assertThat(result).isNotNull();
            assertThat(result.getHoldings()).hasSize(1);
            assertThat(result.getHoldings().get(0).getNameOfIssuer()).isEqualTo("TEST CORP");
        }
    }

    @Nested
    @DisplayName("Handle Edge Cases")
    class HandleEdgeCases {

        @Test
        @DisplayName("should return null for empty XML")
        void shouldReturnNullForEmptyXml() {
            Form13F result = parser.parseInformationTable("", "0001234567-24-000001");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for null XML")
        void shouldReturnNullForNullXml() {
            Form13F result = parser.parseInformationTable(null, "0001234567-24-000001");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should handle XML with extra whitespace")
        void shouldHandleXmlWithWhitespace() {
            String xml = """


                <?xml version="1.0" encoding="UTF-8"?>
                <informationTable xmlns="http://www.sec.gov/edgar/document/thirteenf/informationtable">
                    <infoTable>
                        <nameOfIssuer>  WHITESPACE CORP  </nameOfIssuer>
                        <titleOfClass>COM</titleOfClass>
                        <cusip>123456789</cusip>
                        <value>50000</value>
                        <shrsOrPrnAmt>
                            <sshPrnamt>500</sshPrnamt>
                            <sshPrnamtType>SH</sshPrnamtType>
                        </shrsOrPrnAmt>
                        <investmentDiscretion>SOLE</investmentDiscretion>
                        <votingAuthority>
                            <Sole>500</Sole>
                            <Shared>0</Shared>
                            <None>0</None>
                        </votingAuthority>
                    </infoTable>
                </informationTable>
                """;

            Form13F result = parser.parseInformationTable(xml, "0001234567-24-000003");

            assertThat(result).isNotNull();
            assertThat(result.getHoldings()).hasSize(1);
        }

        @Test
        @DisplayName("should handle principal amount type PRN")
        void shouldHandlePrincipalAmountType() {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <informationTable xmlns="http://www.sec.gov/edgar/document/thirteenf/informationtable">
                    <infoTable>
                        <nameOfIssuer>BOND FUND</nameOfIssuer>
                        <titleOfClass>NOTES</titleOfClass>
                        <cusip>987654321</cusip>
                        <value>1000000</value>
                        <shrsOrPrnAmt>
                            <sshPrnamt>1000000</sshPrnamt>
                            <sshPrnamtType>PRN</sshPrnamtType>
                        </shrsOrPrnAmt>
                        <investmentDiscretion>SOLE</investmentDiscretion>
                        <votingAuthority>
                            <Sole>0</Sole>
                            <Shared>0</Shared>
                            <None>1000000</None>
                        </votingAuthority>
                    </infoTable>
                </informationTable>
                """;

            Form13F result = parser.parseInformationTable(xml, "0001234567-24-000004");

            assertThat(result).isNotNull();
            Form13FHolding holding = result.getHoldings().get(0);
            assertThat(holding.getSharesOrPrincipalAmountType()).isEqualTo("PRN");
            assertThat(holding.getVotingAuthorityNone()).isEqualTo(1000000L);
        }
    }
}
