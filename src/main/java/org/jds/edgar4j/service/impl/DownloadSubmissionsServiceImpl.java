package org.jds.edgar4j.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.Submissions;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.integration.model.SecSubmissionResponse;
import org.jds.edgar4j.repository.FillingRepository;
import org.jds.edgar4j.repository.SubmissionsRepository;
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
    private final SubmissionsRepository submissionsRepository;
    private final FillingRepository fillingRepository;

    @Override
    public long downloadSubmissions(String cik) {
        log.info("Download submissions for CIK: {}", cik);

        try {
            long cikLong = Long.parseLong(cik);
            log.debug("Parsed CIK: {}", cikLong);
        } catch (NumberFormatException e) {
            log.error("CIK is not a number: {}", cik);
            throw new IllegalArgumentException("Invalid CIK format: " + cik);
        }

        String jsonResponse = secApiClient.fetchSubmissions(cik);
        log.debug("Received response length: {} characters", jsonResponse.length());

        SecSubmissionResponse response = responseParser.parseSubmissionResponse(jsonResponse);
        log.info("Parsed submissions for company: {}", response.getName());

        Submissions submissions = responseParser.toSubmissions(response);

        Submissions existingSubmissions = submissionsRepository.findByCik(cik).orElse(null);
        if (existingSubmissions != null) {
            submissions.setId(existingSubmissions.getId());
            log.info("Updating existing submissions for CIK: {}", cik);
        } else {
            log.info("Creating new submissions for CIK: {}", cik);
        }

        submissionsRepository.save(submissions);
        log.info("Saved submissions for CIK: {}", cik);

        List<Filling> fillings = responseParser.toFillings(response);
        log.info("Parsed {} filings for CIK: {}", fillings.size(), cik);

        Map<String, Filling> uniqueFillingsByAccession = new LinkedHashMap<>();
        int filingsWithoutAccession = 0;

        for (Filling filling : fillings) {
            if (filling.getAccessionNumber() != null) {
                uniqueFillingsByAccession.putIfAbsent(filling.getAccessionNumber(), filling);
                Filling existingFilling = fillingRepository.findByAccessionNumber(filling.getAccessionNumber()).orElse(null);
                if (existingFilling != null) {
                    uniqueFillingsByAccession.get(filling.getAccessionNumber()).setId(existingFilling.getId());
                }
            } else {
                filingsWithoutAccession++;
            }
        }

        List<Filling> fillingsToSave = List.copyOf(uniqueFillingsByAccession.values());
        if (filingsWithoutAccession > 0) {
            log.warn("Skipping {} filings without accession number for CIK: {}", filingsWithoutAccession, cik);
        }

        fillingRepository.saveAll(fillingsToSave);
        log.info("Saved {} filings for CIK: {}", fillingsToSave.size(), cik);
        return fillingsToSave.size();
    }
}

