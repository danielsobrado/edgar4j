package org.jds.edgar4j.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.jds.edgar4j.model.Form13DG;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Form13DGParser.
 */
@DisplayName("Form 13D/G Parser Tests")
class Form13DGParserTest {

    private Form13DGParser parser;

    @BeforeEach
    void setUp() {
        parser = new Form13DGParser();
    }

    @Nested
    @DisplayName("Parse Schedule 13D")
    class ParseSchedule13D {

        @Test
        @DisplayName("should parse sample 13D with activist investor")
        void shouldParseSample13D() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13dg/sample_sc13d.xml"));
            String accessionNumber = "0001234567-24-000001";

            Form13DG result = parser.parse(xml, accessionNumber);

            assertThat(result).isNotNull();
            assertThat(result.getAccessionNumber()).isEqualTo(accessionNumber);
            assertThat(result.getFormType()).isEqualTo("SCHEDULE 13D");
            assertThat(result.getScheduleType()).isEqualTo("13D");
            assertThat(result.is13D()).isTrue();
            assertThat(result.is13G()).isFalse();
        }

        @Test
        @DisplayName("should parse issuer information correctly")
        void shouldParseIssuerInfo() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13dg/sample_sc13d.xml"));

            Form13DG result = parser.parse(xml, "0001234567-24-000001");

            assertThat(result.getIssuerName()).isEqualTo("ACME CORPORATION");
            assertThat(result.getIssuerCik()).isEqualTo("0009876543");
            assertThat(result.getCusip()).isEqualTo("001234567");
            assertThat(result.getSecurityTitle()).isEqualTo("Common Stock");
        }

        @Test
        @DisplayName("should parse filing person information correctly")
        void shouldParseFilingPersonInfo() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13dg/sample_sc13d.xml"));

            Form13DG result = parser.parse(xml, "0001234567-24-000001");

            assertThat(result.getFilingPersonName()).isEqualTo("ACTIVIST CAPITAL PARTNERS LP");
            assertThat(result.getFilingPersonCik()).isEqualTo("0001234567");
            assertThat(result.getCitizenshipOrOrganization()).isEqualTo("Delaware");
            assertThat(result.getReportingPersonTypes()).contains("PN", "IA");
        }

        @Test
        @DisplayName("should parse address correctly")
        void shouldParseAddress() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13dg/sample_sc13d.xml"));

            Form13DG result = parser.parse(xml, "0001234567-24-000001");

            assertThat(result.getFilingPersonAddress()).isNotNull();
            assertThat(result.getFilingPersonAddress().getStreet1()).isEqualTo("100 Park Avenue");
            assertThat(result.getFilingPersonAddress().getStreet2()).isEqualTo("Suite 2000");
            assertThat(result.getFilingPersonAddress().getCity()).isEqualTo("New York");
            assertThat(result.getFilingPersonAddress().getStateOrCountry()).isEqualTo("NY");
            assertThat(result.getFilingPersonAddress().getZipCode()).isEqualTo("10001");
        }

        @Test
        @DisplayName("should parse ownership information correctly")
        void shouldParseOwnershipInfo() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13dg/sample_sc13d.xml"));

            Form13DG result = parser.parse(xml, "0001234567-24-000001");

            assertThat(result.getVotingPowerSole()).isEqualTo(5000000L);
            assertThat(result.getVotingPowerShared()).isEqualTo(1000000L);
            assertThat(result.getDispositivePowerSole()).isEqualTo(5000000L);
            assertThat(result.getDispositivePowerShared()).isEqualTo(1000000L);
            assertThat(result.getSharesBeneficiallyOwned()).isEqualTo(6000000L);
            assertThat(result.getPercentOfClass()).isEqualTo(7.5);
            assertThat(result.getTotalVotingPower()).isEqualTo(6000000L);
            assertThat(result.getTotalDispositivePower()).isEqualTo(6000000L);
        }

        @Test
        @DisplayName("should parse dates correctly")
        void shouldParseDates() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13dg/sample_sc13d.xml"));

            Form13DG result = parser.parse(xml, "0001234567-24-000001");

            assertThat(result.getEventDate()).isEqualTo(LocalDate.of(2024, 12, 15));
            assertThat(result.getFiledDate()).isEqualTo(LocalDate.of(2024, 12, 20));
            assertThat(result.getSignatureDate()).isEqualTo(LocalDate.of(2024, 12, 20));
        }

        @Test
        @DisplayName("should parse signature block correctly")
        void shouldParseSignatureBlock() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13dg/sample_sc13d.xml"));

            Form13DG result = parser.parse(xml, "0001234567-24-000001");

            assertThat(result.getSignatureName()).isEqualTo("John Smith");
            assertThat(result.getSignatureTitle()).isEqualTo("Managing Partner");
        }

        @Test
        @DisplayName("should parse additional reporting persons")
        void shouldParseAdditionalReportingPersons() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13dg/sample_sc13d.xml"));

            Form13DG result = parser.parse(xml, "0001234567-24-000001");

            assertThat(result.getAdditionalReportingPersons()).hasSize(1);
            Form13DG.ReportingPerson additional = result.getAdditionalReportingPersons().get(0);
            assertThat(additional.getName()).isEqualTo("ACTIVIST CAPITAL GP LLC");
            assertThat(additional.getCik()).isEqualTo("0001234568");
            assertThat(additional.getSharesBeneficiallyOwned()).isEqualTo(6000000L);
            assertThat(additional.getPercentOfClass()).isEqualTo(7.5);
        }

        @Test
        @DisplayName("should detect initial filing (not amendment)")
        void shouldDetectInitialFiling() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13dg/sample_sc13d.xml"));

            Form13DG result = parser.parse(xml, "0001234567-24-000001");

            assertThat(result.getAmendmentType()).isEqualTo("INITIAL");
            assertThat(result.isAmendment()).isFalse();
        }
    }

    @Nested
    @DisplayName("Parse Schedule 13G")
    class ParseSchedule13G {

        @Test
        @DisplayName("should parse sample 13G with passive investor")
        void shouldParseSample13G() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13dg/sample_sc13g.xml"));
            String accessionNumber = "0002345678-25-000001";

            Form13DG result = parser.parse(xml, accessionNumber);

            assertThat(result).isNotNull();
            assertThat(result.getAccessionNumber()).isEqualTo(accessionNumber);
            assertThat(result.getFormType()).isEqualTo("SCHEDULE 13G");
            assertThat(result.getScheduleType()).isEqualTo("13G");
            assertThat(result.is13G()).isTrue();
            assertThat(result.is13D()).isFalse();
        }

        @Test
        @DisplayName("should parse 13G issuer information correctly")
        void shouldParse13GIssuerInfo() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13dg/sample_sc13g.xml"));

            Form13DG result = parser.parse(xml, "0002345678-25-000001");

            assertThat(result.getIssuerName()).isEqualTo("TECH INNOVATIONS INC");
            assertThat(result.getIssuerCik()).isEqualTo("0008765432");
            assertThat(result.getCusip()).isEqualTo("880088009");
            assertThat(result.getSecurityTitle()).isEqualTo("Class A Common Stock");
        }

        @Test
        @DisplayName("should parse 13G filing person information correctly")
        void shouldParse13GFilingPersonInfo() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13dg/sample_sc13g.xml"));

            Form13DG result = parser.parse(xml, "0002345678-25-000001");

            assertThat(result.getFilingPersonName()).isEqualTo("VANGUARD GROUP INC");
            assertThat(result.getFilingPersonCik()).isEqualTo("0002345678");
            assertThat(result.getCitizenshipOrOrganization()).isEqualTo("Pennsylvania");
            assertThat(result.getReportingPersonTypes()).contains("IA", "IC");
        }

        @Test
        @DisplayName("should parse 13G ownership with dispositive power only")
        void shouldParse13GOwnershipInfo() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13dg/sample_sc13g.xml"));

            Form13DG result = parser.parse(xml, "0002345678-25-000001");

            // 13G filers typically have dispositive power but no voting power
            assertThat(result.getVotingPowerSole()).isEqualTo(0L);
            assertThat(result.getVotingPowerShared()).isEqualTo(0L);
            assertThat(result.getDispositivePowerSole()).isEqualTo(12500000L);
            assertThat(result.getDispositivePowerShared()).isEqualTo(500000L);
            assertThat(result.getSharesBeneficiallyOwned()).isEqualTo(13000000L);
            assertThat(result.getPercentOfClass()).isEqualTo(9.8);
            assertThat(result.isTenPercentOwner()).isFalse();
        }

        @Test
        @DisplayName("should parse 13G without optional street2")
        void shouldParseAddressWithoutStreet2() throws IOException {
            String xml = Files.readString(Path.of("src/test/resources/form13dg/sample_sc13g.xml"));

            Form13DG result = parser.parse(xml, "0002345678-25-000001");

            assertThat(result.getFilingPersonAddress()).isNotNull();
            assertThat(result.getFilingPersonAddress().getStreet1()).isEqualTo("100 Vanguard Blvd");
            assertThat(result.getFilingPersonAddress().getStreet2()).isNull();
            assertThat(result.getFilingPersonAddress().getCity()).isEqualTo("Malvern");
        }
    }

    @Nested
    @DisplayName("Handle Edge Cases")
    class HandleEdgeCases {

        @Test
        @DisplayName("should return null for empty XML")
        void shouldReturnNullForEmptyXml() {
            Form13DG result = parser.parse("", "0001234567-24-000001");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for null XML")
        void shouldReturnNullForNullXml() {
            Form13DG result = parser.parse(null, "0001234567-24-000001");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should parse minimal valid XML")
        void shouldParseMinimalXml() {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <edgarSubmission>
                    <headerData>
                        <submissionType>SC 13D</submissionType>
                    </headerData>
                    <formData>
                        <coverPage>
                            <issuerName>MINIMAL CORP</issuerName>
                            <cusipNumber>999888777</cusipNumber>
                        </coverPage>
                        <reportingPersonInfo>
                            <nameOfReportingPerson>TEST INVESTOR</nameOfReportingPerson>
                        </reportingPersonInfo>
                    </formData>
                </edgarSubmission>
                """;

            Form13DG result = parser.parse(xml, "0001234567-24-000002");

            assertThat(result).isNotNull();
            assertThat(result.getFormType()).isEqualTo("SC 13D");
            assertThat(result.getScheduleType()).isEqualTo("13D");
            assertThat(result.getIssuerName()).isEqualTo("MINIMAL CORP");
            assertThat(result.getCusip()).isEqualTo("999888777");
            assertThat(result.getFilingPersonName()).isEqualTo("TEST INVESTOR");
        }

        @Test
        @DisplayName("should normalize CUSIP by removing special characters")
        void shouldNormalizeCusip() {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <edgarSubmission>
                    <headerData>
                        <submissionType>SC 13G</submissionType>
                    </headerData>
                    <formData>
                        <coverPage>
                            <issuerName>TEST CORP</issuerName>
                            <cusipNumber>123-456-789</cusipNumber>
                        </coverPage>
                        <reportingPersonInfo>
                            <nameOfReportingPerson>TEST INVESTOR</nameOfReportingPerson>
                        </reportingPersonInfo>
                    </formData>
                </edgarSubmission>
                """;

            Form13DG result = parser.parse(xml, "0001234567-24-000003");

            assertThat(result.getCusip()).isEqualTo("123456789");
        }

        @Test
        @DisplayName("should parse amendment correctly")
        void shouldParseAmendment() {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <edgarSubmission>
                    <headerData>
                        <submissionType>SC 13D/A</submissionType>
                    </headerData>
                    <formData>
                        <coverPage>
                            <issuerName>AMENDED CORP</issuerName>
                            <cusipNumber>111222333</cusipNumber>
                            <isAmendment>true</isAmendment>
                            <amendmentNo>2</amendmentNo>
                        </coverPage>
                        <reportingPersonInfo>
                            <nameOfReportingPerson>AMENDER</nameOfReportingPerson>
                        </reportingPersonInfo>
                    </formData>
                </edgarSubmission>
                """;

            Form13DG result = parser.parse(xml, "0001234567-24-000004");

            assertThat(result.getAmendmentType()).isEqualTo("AMENDMENT");
            assertThat(result.getAmendmentNumber()).isEqualTo(2);
            assertThat(result.isAmendment()).isTrue();
        }

        @Test
        @DisplayName("should detect ten percent owner")
        void shouldDetectTenPercentOwner() {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <edgarSubmission>
                    <headerData>
                        <submissionType>SC 13D</submissionType>
                    </headerData>
                    <formData>
                        <coverPage>
                            <issuerName>BIG STAKE CORP</issuerName>
                            <cusipNumber>444555666</cusipNumber>
                        </coverPage>
                        <reportingPersonInfo>
                            <nameOfReportingPerson>BIG INVESTOR</nameOfReportingPerson>
                            <ownershipInfo>
                                <aggregateAmountBeneficiallyOwned>15000000</aggregateAmountBeneficiallyOwned>
                                <percentOfClass>15.5</percentOfClass>
                            </ownershipInfo>
                        </reportingPersonInfo>
                    </formData>
                </edgarSubmission>
                """;

            Form13DG result = parser.parse(xml, "0001234567-24-000005");

            assertThat(result.getPercentOfClass()).isEqualTo(15.5);
            assertThat(result.isTenPercentOwner()).isTrue();
        }

        @Test
        @DisplayName("should handle XML with extra whitespace")
        void shouldHandleXmlWithWhitespace() {
            String xml = """


                <?xml version="1.0" encoding="UTF-8"?>
                <edgarSubmission>
                    <headerData>
                        <submissionType>  SC 13G  </submissionType>
                    </headerData>
                    <formData>
                        <coverPage>
                            <issuerName>  WHITESPACE CORP  </issuerName>
                            <cusipNumber>777888999</cusipNumber>
                        </coverPage>
                        <reportingPersonInfo>
                            <nameOfReportingPerson>SPACE INVESTOR</nameOfReportingPerson>
                        </reportingPersonInfo>
                    </formData>
                </edgarSubmission>
                """;

            Form13DG result = parser.parse(xml, "0001234567-24-000006");

            assertThat(result).isNotNull();
            assertThat(result.getScheduleType()).isEqualTo("13G");
        }
    }

    @Nested
    @DisplayName("Helper Methods")
    class HelperMethods {

        @Test
        @DisplayName("isTenPercentOwner returns false for null percent")
        void isTenPercentOwnerReturnsFalseForNull() {
            Form13DG form = new Form13DG();
            form.setPercentOfClass(null);

            assertThat(form.isTenPercentOwner()).isFalse();
        }

        @Test
        @DisplayName("getTotalVotingPower handles null values")
        void getTotalVotingPowerHandlesNull() {
            Form13DG form = new Form13DG();
            form.setVotingPowerSole(null);
            form.setVotingPowerShared(1000L);

            assertThat(form.getTotalVotingPower()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("getTotalDispositivePower handles null values")
        void getTotalDispositivePowerHandlesNull() {
            Form13DG form = new Form13DG();
            form.setDispositivePowerSole(5000L);
            form.setDispositivePowerShared(null);

            assertThat(form.getTotalDispositivePower()).isEqualTo(5000L);
        }
    }
}
