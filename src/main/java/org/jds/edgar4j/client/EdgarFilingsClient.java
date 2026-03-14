package org.jds.edgar4j.client;

import org.jds.edgar4j.model.search.FilingSearch;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;

@Component
public class EdgarFilingsClient {

    private final RestTemplate restTemplate;

    public EdgarFilingsClient() {
        this.restTemplate = new RestTemplate();
    }

    public List<FilingSearch> getFilings(String cikNo, String formType, int filingYear, String userAgent) {
        String url = UriComponentsBuilder.fromUriString("https://api.example.com/filings")
            .queryParam("cikNo", cikNo)
            .queryParam("formType", formType)
            .queryParam("filingYear", filingYear)
            .encode()
            .toUriString();

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
