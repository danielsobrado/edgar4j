package org.jds.edgar4j.service.impl;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.Submissions;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.integration.model.SecSubmissionResponse;
import org.jds.edgar4j.integration.model.SecSubmissionResponse.FilesEntry;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.port.SubmissionsDataPort;
import org.jds.edgar4j.service.DownloadSubmissionsService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2022-09-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadSubmissionsServiceImpl implements DownloadSubmissionsService {

    private final SecApiClient secApiClient;
    private final SecResponseParser responseParser;
    private final SubmissionsDataPort submissionsRepository;
    private final FillingDataPort fillingRepository;

    @Override
    public long downloadSubmissions(String cik) {
        log.info("Download submissions for CIK: {}", cik);

        if (cik == null || cik.isBlank()) {
            throw new IllegalArgumentException("CIK is required");
        }

        String normalizedCik = cik.trim();
        try {
            long cikLong = Long.parseLong(normalizedCik);
            log.debug("Parsed CIK: {}", cikLong);
        } catch (NumberFormatException e) {
            log.error("CIK is not a number: {}", cik);
            throw new IllegalArgumentException("Invalid CIK format: " + cik);
        }

        String jsonResponse = secApiClient.fetchSubmissions(normalizedCik);
        log.debug("Received response length: {} characters", jsonResponse.length());

        SecSubmissionResponse response = responseParser.parseSubmissionResponse(jsonResponse);
        log.info("Parsed submissions for company: {}", response.getName());

        List<Filling> fillings = responseParser.toFillings(response);
        return saveSubmissionsAndFillings(normalizedCik, response, fillings, "CIK sync");
    }

    @Override
    public long downloadSubmissions(String cik, String formType, LocalDate filingDateFrom, LocalDate filingDateTo) {
        log.info("Download filings for CIK: {} with formType={}, filingDateFrom={}, filingDateTo={}",
                cik,
                formType,
                filingDateFrom,
                filingDateTo);

        if (cik == null || cik.isBlank()) {
            throw new IllegalArgumentException("CIK is required");
        }

        if ((filingDateFrom == null) != (filingDateTo == null)) {
            throw new IllegalArgumentException("Both filingDateFrom and filingDateTo are required");
        }

        if (filingDateFrom != null && filingDateTo != null && filingDateTo.isBefore(filingDateFrom)) {
            throw new IllegalArgumentException("filingDateFrom must be on or before filingDateTo");
        }

        String normalizedCik = cik.trim();
        try {
            Long.parseLong(normalizedCik);
        } catch (NumberFormatException e) {
            log.error("CIK is not a number: {}", cik);
            throw new IllegalArgumentException("Invalid CIK format: " + cik);
        }

        String jsonResponse = secApiClient.fetchSubmissions(normalizedCik);
        log.debug("Received response length: {} characters", jsonResponse.length());

        SecSubmissionResponse response = responseParser.parseSubmissionResponse(jsonResponse);
        log.info("Parsed filings for company: {}", response.getName());

        List<Filling> filingsFromRecentResponse = responseParser.toFillings(response);
        List<Filling> filingsFromArchive = loadArchivedFilings(response, normalizedCik, filingDateFrom, filingDateTo);
        List<Filling> fillings = new ArrayList<>(filingsFromRecentResponse.size() + filingsFromArchive.size());
        fillings.addAll(filingsFromRecentResponse);
        fillings.addAll(filingsFromArchive);

        List<Filling> matchedFilings = fillings.stream()
                .filter(filling -> filingTypeMatches(filling, formType))
                .filter(filling -> filingDateMatches(filling, filingDateFrom, filingDateTo))
                .toList();
        long saved = saveSubmissionsAndFillings(normalizedCik, response, matchedFilings, "filing-date sync");
        if (saved == 0 && !fillings.isEmpty()) {
            log.warn(
                    "No new filings persisted for CIK {} in date range {} to {} (mode: filing-date).",
                    normalizedCik,
                    filingDateFrom,
                    filingDateTo);
        } else if (saved == 0 && filingDateFrom != null && filingDateTo != null) {
            log.warn(
                    "No filings matched form {} and date window {} to {} for CIK {}",
                    formType,
                    filingDateFrom,
                    filingDateTo,
                    normalizedCik);
        }
        return saved;
    }

    private long saveSubmissionsAndFillings(
            String normalizedCik,
            SecSubmissionResponse response,
            List<Filling> fillings,
            String syncMode) {
        Submissions submissions = responseParser.toSubmissions(response);
        Submissions existingSubmissions = submissionsRepository.findByCik(normalizedCik).orElse(null);
        if (existingSubmissions != null) {
            submissions.setId(existingSubmissions.getId());
            log.info("Updating existing submissions for CIK: {} ({})", normalizedCik, syncMode);
        } else {
            log.info("Creating new submissions for CIK: {} ({})", normalizedCik, syncMode);
        }
        submissions.setCik(normalizedCik);
        submissionsRepository.save(submissions);
        log.info("Saved submissions for CIK: {} ({})", normalizedCik, syncMode);

        Map<String, Filling> uniqueFillingsByAccession = new LinkedHashMap<>();
        int filingsWithoutAccession = 0;
        for (Filling filling : fillings) {
            if (filling.getAccessionNumber() == null) {
                filingsWithoutAccession++;
                continue;
            }
            uniqueFillingsByAccession.putIfAbsent(filling.getAccessionNumber(), filling);
        }

        for (Filling filling : uniqueFillingsByAccession.values()) {
            Filling existingFilling = fillingRepository.findByAccessionNumber(filling.getAccessionNumber()).orElse(null);
            if (existingFilling != null) {
                filling.setId(existingFilling.getId());
            }
            filling.setCik(normalizedCik);
        }

        List<Filling> fillingsToSave = new ArrayList<>(uniqueFillingsByAccession.values());
        if (filingsWithoutAccession > 0) {
            log.warn(
                    "Skipping {} filings without accession number for CIK: {} ({})",
                    filingsWithoutAccession,
                    normalizedCik,
                    syncMode);
        }
        if (!fillingsToSave.isEmpty()) {
            fillingRepository.saveAll(fillingsToSave);
            log.info("Saved {} filings for CIK: {} ({})", fillingsToSave.size(), normalizedCik, syncMode);
        } else {
            log.info("Saved 0 filings for CIK: {} ({})", normalizedCik, syncMode);
        }
        return fillingsToSave.size();
    }

    private List<Filling> loadArchivedFilings(
            SecSubmissionResponse response,
            String normalizedCik,
            LocalDate filingDateFrom,
            LocalDate filingDateTo) {
        if (response == null
                || response.getFilings() == null
                || response.getFilings().getFiles() == null
                || response.getFilings().getFiles().isEmpty()) {
            return List.of();
        }

        Set<String> uniqueFileNames = new HashSet<>();
        Set<String> processedFileNames = new HashSet<>();
        for (FilesEntry fileEntry : response.getFilings().getFiles()) {
            if (fileEntry == null || fileEntry.getName() == null || fileEntry.getName().isBlank()) {
                continue;
            }

            if (!archiveRangeOverlaps(filingDateFrom, filingDateTo, fileEntry.getFilingFrom(), fileEntry.getFilingTo())) {
                continue;
            }
            uniqueFileNames.add(fileEntry.getName());
        }

        List<Filling> filings = new ArrayList<>();
        for (String fileName : uniqueFileNames) {
            if (!processedFileNames.add(fileName)) {
                continue;
            }

            try {
                String fileJson = secApiClient.fetchSubmissionFile(fileName);
                filings.addAll(responseParser.toFillings(fileJson, normalizedCik));
            } catch (RuntimeException e) {
                log.warn("Failed to load archived filing file {} for CIK {}: {}", fileName, normalizedCik, e.getMessage());
            }
        }

        return filings;
    }

    private boolean archiveRangeOverlaps(
            LocalDate filingDateFrom,
            LocalDate filingDateTo,
            String filingFromText,
            String filingToText) {
        LocalDate filingFrom;
        LocalDate filingTo;
        try {
            filingFrom = filingFromText == null || filingFromText.isBlank() ? null : LocalDate.parse(filingFromText);
            filingTo = filingToText == null || filingToText.isBlank() ? null : LocalDate.parse(filingToText);
        } catch (DateTimeParseException e) {
            return false;
        }

        if (filingFrom == null && filingTo == null) {
            return false;
        }
        if (filingFrom == null) {
            filingFrom = filingTo;
        }
        if (filingTo == null) {
            filingTo = filingFrom;
        }
        return !filingFrom.isAfter(filingDateTo) && !filingTo.isBefore(filingDateFrom);
    }

    private boolean filingTypeMatches(Filling filling, String formType) {
        if (formType == null || formType.isBlank()) {
            return true;
        }
        if (filling == null || filling.getFormType() == null || filling.getFormType().getNumber() == null) {
            return false;
        }
        return formType.equalsIgnoreCase(filling.getFormType().getNumber());
    }

    private boolean filingDateMatches(Filling filling, LocalDate filingDateFrom, LocalDate filingDateTo) {
        if (filingDateFrom == null && filingDateTo == null) {
            return true;
        }
        if (filling == null || filling.getFillingDate() == null) {
            return false;
        }

        LocalDate filingDate = filling.getFillingDate()
                .toInstant()
                .atZone(ZoneOffset.UTC)
                .toLocalDate();

        return (filingDateFrom == null || !filingDate.isBefore(filingDateFrom))
                && (filingDateTo == null || !filingDate.isAfter(filingDateTo));
    }
}

