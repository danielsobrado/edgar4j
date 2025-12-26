package org.jds.edgar4j.service.impl;

import java.util.List;

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
    public void downloadSubmissions(String cik) {
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

        for (Filling filling : fillings) {
            if (filling.getAccessionNumber() != null) {
                Filling existingFilling = fillingRepository.findByAccessionNumber(filling.getAccessionNumber()).orElse(null);
                if (existingFilling != null) {
                    filling.setId(existingFilling.getId());
                }
            }
        }

        fillingRepository.saveAll(fillings);
        log.info("Saved {} filings for CIK: {}", fillings.size(), cik);
    }
}

