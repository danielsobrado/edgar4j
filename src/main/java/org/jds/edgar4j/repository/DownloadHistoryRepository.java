package org.jds.edgar4j.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.DownloadHistory;
import org.jds.edgar4j.model.DownloadHistory.ProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Form 4 download history
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
@Repository
public interface DownloadHistoryRepository extends ElasticsearchRepository<DownloadHistory, String> {

    /**
     * Find by accession number
     */
    Optional<DownloadHistory> findByAccessionNumber(String accessionNumber);

    /**
     * Check if accession number exists
     */
    boolean existsByAccessionNumber(String accessionNumber);

    /**
     * Find by status
     */
    Page<DownloadHistory> findByStatus(ProcessingStatus status, Pageable pageable);

    /**
     * Find by filing date range
     */
    Page<DownloadHistory> findByFilingDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Find by status and filing date range
     */
    Page<DownloadHistory> findByStatusAndFilingDateBetween(
        ProcessingStatus status, LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Find failed downloads for retry
     */
    List<DownloadHistory> findByStatusAndRetryCountLessThan(ProcessingStatus status, int maxRetries);

    /**
     * Count by status
     */
    long countByStatus(ProcessingStatus status);

    /**
     * Count by filing date
     */
    long countByFilingDate(LocalDate filingDate);

    /**
     * Count by status and filing date
     */
    long countByStatusAndFilingDate(ProcessingStatus status, LocalDate filingDate);

    /**
     * Find by CIK
     */
    Page<DownloadHistory> findByCik(String cik, Pageable pageable);

    /**
     * Delete old records by filing date before
     */
    void deleteByFilingDateBefore(LocalDate date);
}
