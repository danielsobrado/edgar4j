package org.jds.edgar4j.adapter.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.model.Form13DG;
import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.model.Form13FHolding;
import org.jds.edgar4j.model.Submissions;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.jds.edgar4j.storage.file.FileStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class IndexedFileAdaptersTest {

    @TempDir
    Path tempDir;

    @Test
    void form13dgUsesIndexedExactLookups() {
        Form13DGFileAdapter adapter = new Form13DGFileAdapter(newStorageEngine());

        adapter.saveAll(List.of(
                Form13DG.builder()
                        .accessionNumber("0001")
                        .formType("SC 13D")
                        .scheduleType("13D")
                        .issuerCik("issuer-1")
                        .issuerName("Acme Corp")
                        .filingPersonCik("owner-1")
                        .filingPersonName("Owner One")
                        .cusip("CUSIP-1")
                        .amendmentType("INITIAL")
                        .eventDate(LocalDate.of(2024, 12, 15))
                        .purposeOfTransaction("Activist")
                        .build(),
                Form13DG.builder()
                        .accessionNumber("0002")
                        .formType("SC 13G")
                        .scheduleType("13G")
                        .issuerCik("issuer-1")
                        .issuerName("Acme Corp")
                        .filingPersonCik("owner-1")
                        .filingPersonName("Owner One")
                        .cusip("CUSIP-1")
                        .amendmentType("AMENDMENT")
                        .eventDate(LocalDate.of(2024, 12, 20))
                        .build()));

        assertThat(adapter.findByAccessionNumber("0001")).isPresent();
        assertThat(adapter.findByCusip("CUSIP-1")).hasSize(2);
        assertThat(adapter.findByScheduleType("13D", PageRequest.of(0, 10)).getContent()).hasSize(1);
        assertThat(adapter.countByScheduleType("13g")).isEqualTo(1);
        assertThat(adapter.findAmendments(PageRequest.of(0, 10)).getContent()).hasSize(1);
        assertThat(adapter.getOwnershipHistory("owner-1", "issuer-1")).hasSize(2);
    }

    @Test
    void form13fUsesIndexedPeriodAndHoldingLookups() {
        Form13FFileAdapter adapter = new Form13FFileAdapter(newStorageEngine());
        LocalDate q3 = LocalDate.of(2024, 9, 30);
        LocalDate q2 = LocalDate.of(2024, 6, 30);

        adapter.saveAll(List.of(
                Form13F.builder()
                        .accessionNumber("13f-1")
                        .cik("manager-1")
                        .filerName("Manager One")
                        .reportPeriod(q3)
                        .totalValue(1000L)
                        .holdingsCount(2)
                        .holdings(List.of(
                                Form13FHolding.builder().cusip("CUSIP-A").nameOfIssuer("Alpha").value(700L).sharesOrPrincipalAmount(7L).build(),
                                Form13FHolding.builder().cusip("CUSIP-B").nameOfIssuer("Beta").value(300L).sharesOrPrincipalAmount(3L).build()))
                        .build(),
                Form13F.builder()
                        .accessionNumber("13f-2")
                        .cik("manager-1")
                        .filerName("Manager One")
                        .reportPeriod(q2)
                        .totalValue(900L)
                        .holdingsCount(1)
                        .holdings(List.of(Form13FHolding.builder().cusip("CUSIP-A").nameOfIssuer("Alpha").value(900L).sharesOrPrincipalAmount(9L).build()))
                        .build(),
                Form13F.builder()
                        .accessionNumber("13f-3")
                        .cik("manager-2")
                        .filerName("Manager Two")
                        .reportPeriod(q3)
                        .totalValue(1200L)
                        .holdingsCount(1)
                        .holdings(List.of(Form13FHolding.builder().cusip("CUSIP-C").nameOfIssuer("Gamma").value(1200L).sharesOrPrincipalAmount(12L).build()))
                        .build()));

        assertThat(adapter.findByAccessionNumber("13f-1")).isPresent();
        assertThat(adapter.findByCik("manager-1", PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "reportPeriod"))).getContent()).hasSize(2);
        assertThat(adapter.findByReportPeriod(q3, PageRequest.of(0, 10)).getContent()).hasSize(2);
        assertThat(adapter.findByHoldingCusip("CUSIP-A")).hasSize(2);
        assertThat(adapter.existsByCikAndReportPeriod("manager-1", q2)).isTrue();
        assertThat(adapter.getTopFilersByPeriod(q3, 10)).hasSize(2);
        assertThat(adapter.getTopHoldingsByPeriod(q3, 10)).hasSize(3);
        assertThat(adapter.getPortfolioHistory("manager-1")).extracting(snapshot -> snapshot.getReportPeriod()).containsExactly(q3, q2);
    }

    @Test
    void submissionsUsesIndexedTickerLookup() {
        SubmissionsFileAdapter adapter = new SubmissionsFileAdapter(newStorageEngine());

        adapter.saveAll(List.of(
                Submissions.builder().cik("1001").companyName("Acme").tickers(List.of("ACME", "ACM")) .build(),
                Submissions.builder().cik("1002").companyName("Beta").tickers(List.of("BETA")) .build()));

        assertThat(adapter.findByTickersContaining("acme")).hasSize(1);
        assertThat(adapter.findByTickersContaining("beta")).hasSize(1);
    }

    private FileStorageEngine newStorageEngine() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBasePath(tempDir.toString());
        properties.setCollectionsPath("collections");
        properties.setIndexOnStartup(true);
        properties.setFlushOnWrite(true);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new FileStorageEngine(properties, objectMapper);
    }
}