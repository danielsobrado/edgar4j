package org.jds.edgar4j.port;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Form13F;
import org.jds.edgar4j.repository.Form13FRepository.FilerSummary;
import org.jds.edgar4j.repository.Form13FRepository.HoldingSummary;
import org.jds.edgar4j.repository.Form13FRepository.PortfolioSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface Form13FDataPort extends BaseDocumentDataPort<Form13F> {

    Optional<Form13F> findByAccessionNumber(String accessionNumber);

    Page<Form13F> findByCik(String cik, Pageable pageable);

    Page<Form13F> findByFilerNameContainingIgnoreCase(String filerName, Pageable pageable);

    Page<Form13F> findByReportPeriod(LocalDate reportPeriod, Pageable pageable);

    Page<Form13F> findByReportPeriodBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<Form13F> findByCikAndReportPeriodBetween(String cik, LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<Form13F> findByCikAndReportPeriodBetweenList(String cik, LocalDate startDate, LocalDate endDate);

    List<Form13F> findByHoldingCusip(String cusip);

    Page<Form13F> findByHoldingCusip(String cusip, Pageable pageable);

    Page<Form13F> findByHoldingIssuerName(String issuerName, Pageable pageable);

    boolean existsByAccessionNumber(String accessionNumber);

    boolean existsByCikAndReportPeriod(String cik, LocalDate reportPeriod);

    List<FilerSummary> getTopFilersByPeriod(LocalDate reportPeriod, int limit);

    List<HoldingSummary> getTopHoldingsByPeriod(LocalDate reportPeriod, int limit);

    List<PortfolioSnapshot> getPortfolioHistory(String cik);
}
