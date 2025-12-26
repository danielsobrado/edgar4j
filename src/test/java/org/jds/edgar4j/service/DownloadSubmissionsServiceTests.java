package org.jds.edgar4j.service;

import org.jds.edgar4j.service.impl.DownloadSubmissionsServiceImpl;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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

    @DisplayName("JUnit test for testSubmissions() method")
    @Test
    public void testSubmissions() {
        String cik = "789019";
        downloadSubmissionsService.downloadSubmissions(cik);

    }



}
