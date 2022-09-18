package org.jds.edgar4j.service;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.jds.edgar4j.service.impl.Form4ServiceImpl;
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

import static org.junit.jupiter.api.Assertions.*;

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
public class Form4ServiceTests {

    @Autowired
    private Form4ServiceImpl form4Service;

    @DisplayName("JUnit test for testForm4() method")
    @Test
    public void testForm4Download() {
        String cik = "789019";
        String accessionNumber = "0001626431-16-000118";
        String primaryDocument = "xslF345X03/edgar.xml";
        
        try {
            CompletableFuture<HttpResponse<String>> response = form4Service.downloadForm4(cik, accessionNumber, primaryDocument);
            HttpResponse<String> httpResponse = response.get();
            
            assertTrue(httpResponse.body().contains("MICROSOFT CORP"));

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            assertTrue(false);
        }

    }



}
