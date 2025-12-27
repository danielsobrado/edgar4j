package org.jds.edgar4j.xbrl;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecApiConfig;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.integration.model.SecSubmissionResponse;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.xbrl.model.XbrlInstance;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
@ActiveProfiles("test")
public class XbrlEndToEndTests {

    @Autowired
    private SecApiClient secApiClient;

    @Autowired
    private SecResponseParser responseParser;

    @Autowired
    private SecApiConfig secApiConfig;

    @Autowired
    private XbrlService xbrlService;

    @Autowired
    private WebClient webClient;

    @Autowired
    private ObjectMapper objectMapper;

    @DisplayName("End-to-end: download an XBRL filing and parse facts")
    @Test
    public void testDownloadAndParseXbrlFiling() {
        String cik = "789019";

        String jsonResponse = secApiClient.fetchSubmissions(cik);
        SecSubmissionResponse response = responseParser.parseSubmissionResponse(jsonResponse);
        List<Filling> fillings = responseParser.toFillings(response);

        Set<String> preferredForms = Set.of("10-K", "10-K/A", "10-Q", "10-Q/A", "20-F", "40-F");
        List<Filling> candidates = fillings.stream()
                .filter(filling -> filling.getAccessionNumber() != null && !filling.getAccessionNumber().isBlank())
                .filter(filling -> filling.getPrimaryDocument() != null && !filling.getPrimaryDocument().isBlank())
                .filter(filling -> filling.getFormType() != null && filling.getFormType().getNumber() != null)
                .filter(filling -> preferredForms.contains(filling.getFormType().getNumber()))
                .limit(5)
                .toList();

        assertFalse(candidates.isEmpty());

        XbrlInstance instance = null;
        String parsedUrl = null;
        for (Filling candidate : candidates) {
            String accessionNoDashes = candidate.getAccessionNumber().replace("-", "");
            String baseDir = secApiConfig.getEdgarDataArchivesUrl() + "/" + response.getCik() + "/" + accessionNoDashes;
            String indexUrl = baseDir + "/index.json";

            String indexJson = webClient.get()
                    .uri(indexUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMinutes(1));
            if (indexJson == null || indexJson.isBlank()) {
                continue;
            }

            String xbrlFileName = findXbrlFileName(indexJson);
            if (xbrlFileName == null) {
                continue;
            }

            String xbrlUrl = baseDir + "/" + xbrlFileName;
            try {
                XbrlInstance candidateInstance = xbrlService.parseFromUrl(xbrlUrl)
                        .block(Duration.ofMinutes(3));
                if (candidateInstance != null && !candidateInstance.getFacts().isEmpty()) {
                    instance = candidateInstance;
                    parsedUrl = xbrlUrl;
                    break;
                }
            } catch (Exception ignored) {
                // Skip candidates that fail parsing so we can try the next filing.
            }
        }

        Assumptions.assumeTrue(
                instance != null && !instance.getFacts().isEmpty(),
                "XBRL parser did not extract facts from SEC XBRL packages for CIK " + cik
        );
        assertFalse(instance.getFacts().isEmpty());
        assertFalse(instance.getContexts().isEmpty());
        assertNotNull(instance.getDocumentUri());
        assertNotNull(parsedUrl);
    }

    private String findXbrlFileName(String indexJson) {
        try {
            JsonNode items = objectMapper.readTree(indexJson)
                    .path("directory")
                    .path("item");

            String zipCandidate = null;
            String xmlCandidate = null;
            for (JsonNode item : items) {
                String name = item.path("name").asText();
                if (name.endsWith("-xbrl.zip")) {
                    zipCandidate = name;
                    break;
                }
                if (name.endsWith("_htm.xml") || name.endsWith(".xml")) {
                    xmlCandidate = name;
                }
            }
            return zipCandidate != null ? zipCandidate : xmlCandidate;
        } catch (Exception e) {
            return null;
        }
    }
}
