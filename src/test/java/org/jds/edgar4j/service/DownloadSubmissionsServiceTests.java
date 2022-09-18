package org.jds.edgar4j.service;

import org.jds.edgar4j.service.impl.DownloadSubmissionsServiceImpl;
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
