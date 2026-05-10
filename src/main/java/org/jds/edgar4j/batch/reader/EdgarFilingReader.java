package org.jds.edgar4j.batch.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecForm4DocumentSupport;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Spring Batch ItemReader for Edgar Form 4 filings
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class EdgarFilingReader implements ItemReader<String> {
    private static final String SUPPORTED_FORM_TYPE = "FORM4";
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private final SecApiClient secApiClient;

    @Value("#{jobParameters['startDate']}")
    private String startDate;

    @Value("#{jobParameters['endDate']}")
    private String endDate;

    @Value("#{jobParameters['formType'] ?: 'FORM4'}")
    private String formType;

    private Iterator<String> accessionNumberIterator;
    private boolean initialized = false;

    @Override
    public String read() throws Exception {
        if (!initialized) {
            initialize();
            initialized = true;
        }

        if (accessionNumberIterator != null && accessionNumberIterator.hasNext()) {
            String accessionNumber = accessionNumberIterator.next();
            log.debug("Reading accession number: {}", accessionNumber);
            return accessionNumber;
        }

        return null; // Indicates end of data
    }

    private void initialize() {
        try {
            log.info("Initializing EdgarFilingReader with parameters: startDate={}, endDate={}, formType={}",
                    startDate, endDate, formType);

            String normalizedFormType = normalizeFormType(formType);
            if (!SUPPORTED_FORM_TYPE.equals(normalizedFormType)) {
                log.warn("Unsupported formType '{}' configured. Supported types: {}. No filings will be read.",
                        formType, SUPPORTED_FORM_TYPE);
                this.accessionNumberIterator = List.<String>of().iterator();
                return;
            }

            LocalDate start = parseDate(startDate);
            LocalDate end = parseDate(endDate);
            LocalDate[] range = normalizeDateRange(start, end);
            if (range[0] != start || range[1] != end) {
                log.warn("Date range was adjusted: from {} to {} -> from {} to {}",
                        start, end, range[0], range[1]);
            }

            List<String> accessionNumbers = fetchAccessionNumbers(range[0], range[1]);
            this.accessionNumberIterator = accessionNumbers.iterator();

            log.info("Initialized reader with {} accession numbers", accessionNumbers.size());

        } catch (Exception e) {
            log.error("Failed to initialize EdgarFilingReader: {}", e.getMessage(), e);
            this.accessionNumberIterator = new ArrayList<String>().iterator();
        }
    }

    private List<String> fetchAccessionNumbers(LocalDate startDate, LocalDate endDate) {
        List<String> accessionNumbers = new ArrayList<>();

        try {
            log.info("Fetching Form 4 accession numbers from {} to {}", startDate, endDate);

            LocalDate currentDate = startDate;
            while (!currentDate.isAfter(endDate)) {
                if (!isWeekend(currentDate)) {
                    try {
                        secApiClient.fetchDailyMasterIndex(currentDate)
                                .map(SecForm4DocumentSupport::parseDailyMasterIndex)
                                .ifPresent(accessionNumbers::addAll);
                    } catch (Exception e) {
                        log.debug("Daily master index unavailable for {}: {}", currentDate, e.getMessage());
                    }
                }
                currentDate = currentDate.plusDays(1);
            }

            log.info("Successfully fetched {} accession numbers for date range", accessionNumbers.size());

        } catch (Exception e) {
            log.error("Error fetching accession numbers for date range {} to {}: {}",
                    startDate, endDate, e.getMessage(), e);
        }

        return accessionNumbers;
    }

    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return LocalDate.now();
        }

        try {
            return LocalDate.parse(dateString, DateTimeFormatter.ofPattern(DATE_PATTERN));
        } catch (Exception e) {
            log.warn("Failed to parse date: {}, using current date", dateString);
            return LocalDate.now();
        }
    }

    private LocalDate[] normalizeDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDate normalizedStart = startDate;
        LocalDate normalizedEnd = endDate;

        if (normalizedStart != null && normalizedEnd != null && normalizedStart.isAfter(normalizedEnd)) {
            log.warn("startDate {} is after endDate {}; swapping values", normalizedStart, normalizedEnd);
            return new LocalDate[] {normalizedEnd, normalizedStart};
        }

        return new LocalDate[] {normalizedStart, normalizedEnd};
    }

    private String normalizeFormType(String value) {
        if (value == null || value.isBlank()) {
            return SUPPORTED_FORM_TYPE;
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }
}
