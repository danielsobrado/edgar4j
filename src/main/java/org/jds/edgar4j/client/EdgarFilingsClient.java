package org.jds.edgar4j.client;

import org.jds.edgar4j.model.search.FilingSearch;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Component
public class EdgarFilingsClient {

    private final RestTemplate restTemplate;

    public EdgarFilingsClient() {
        this.restTemplate = new RestTemplate();
    }

    public List<FilingSearch> getFilings(String cikNo, String formType, int filingYear, String userAgent) {
        // Replace this URL with the appropriate API or data source URL
        String url = "https://api.example.com/filings?cikNo=" + cikNo + "&formType=" + formType + "&filingYear=" + filingYear;

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", userAgent);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<FilingSearch[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, FilingSearch[].class);

        if (response.getStatusCode().is2xxSuccessful()) {
            return Arrays.asList(response.getBody());
        } else {
            throw new RuntimeException("Failed to fetch filings: " + response.getStatusCode());
        }
    }

}
