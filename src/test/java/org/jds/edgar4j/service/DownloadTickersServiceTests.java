package org.jds.edgar4j.service;

import org.jds.edgar4j.service.impl.DownloadTickersServiceImpl;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * @author J. Daniel Sobrado
 * @version 1.1
 * @since 2022-09-18
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
@TestPropertySource(properties = "spring.mongodb.embedded.version=3.5.5")
public class DownloadTickersServiceTests {

    @Autowired
    private DownloadTickersServiceImpl downloadTickersService;

    @DisplayName("JUnit test for downloadTickers() method")
    @Test
    public void testDownloadTickers() {

        downloadTickersService.downloadTickers();

    }

    @DisplayName("JUnit test for downloadTickersExchanges() method")
    @Test
    public void testDownloadTickersExchanges() {

        downloadTickersService.downloadTickersExchanges();

    }

    @DisplayName("JUnit test for downloadTickersMFs() method")
    @Test
    public void testDownloadTickersMFs() {

        downloadTickersService.downloadTickersMFs();

    }


}
