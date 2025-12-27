package org.jds.edgar4j.service;

import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.Submissions;
import org.jds.edgar4j.repository.FillingRepository;
import org.jds.edgar4j.repository.SubmissionsRepository;
import org.jds.edgar4j.service.impl.DownloadSubmissionsServiceImpl;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author J. Daniel Sobrado
 * @version 1.1
 * @since 2022-09-18
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
@ActiveProfiles("test")
public class DownloadSubmissionsServiceTests {

    @Autowired
    private DownloadSubmissionsServiceImpl downloadSubmissionsService;

    @Autowired
    private SubmissionsRepository submissionsRepository;

    @Autowired
    private FillingRepository fillingRepository;

    @DisplayName("JUnit test for testSubmissions() method")
    @Test
    public void testSubmissions() {
        String cik = "789019";
        downloadSubmissionsService.downloadSubmissions(cik);

        String formattedCik = String.format("%010d", Long.parseLong(cik));
        Submissions submissions = submissionsRepository.findByCik(formattedCik)
                .orElseThrow(() -> new AssertionError("Submissions not saved for CIK " + formattedCik));

        assertNotNull(submissions.getCompanyName());
        assertFalse(submissions.getCompanyName().isBlank());
        assertNotNull(submissions.getCik());
        assertTrue(submissions.getCik().endsWith(cik));

        List<Filling> fillings = fillingRepository.findByCik(formattedCik);
        assertFalse(fillings.isEmpty());

        Filling sampleFiling = fillings.stream()
                .filter(filling -> filling.getAccessionNumber() != null && !filling.getAccessionNumber().isBlank())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No filings with accession numbers saved"));

        assertNotNull(sampleFiling.getPrimaryDocument());
        assertFalse(sampleFiling.getPrimaryDocument().isBlank());
    }



}
