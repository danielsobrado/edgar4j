package org.jds.edgar4j.service;

import org.jds.edgar4j.service.impl.DownloadTickersServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author J. Daniel Sobrado
 * @version 1.1
 * @since 2022-09-18
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@EnableAutoConfiguration(exclude = { EmbeddedMongoAutoConfiguration.class, 
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class })
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
