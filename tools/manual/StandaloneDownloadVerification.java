package org.jds.edgar4j.verification;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class StandaloneDownloadVerification {

    private static final String USER_AGENT = "Edgar4J/1.0 (test@example.com)";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    public static void main(String[] args) {
        System.out.println("=== Edgar4J Download Capabilities Verification ===\n");

        try {
            testCompanyTickersDownload();
            testMicrosoftSubmissions();
            testDailyMasterIndex();
            testForm4DocumentDownload();
        } catch (Exception e) {
            System.err.println("Download verification failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testCompanyTickersDownload() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://www.sec.gov/files/company_tickers.json"))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Company tickers status: " + response.statusCode());
    }

    private static void testMicrosoftSubmissions() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://data.sec.gov/submissions/CIK0000789019.json"))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Microsoft submissions status: " + response.statusCode());
    }

    private static void testDailyMasterIndex() throws IOException, InterruptedException {
        LocalDate testDate = LocalDate.now().minusDays(1);
        String year = String.valueOf(testDate.getYear());
        String quarter = "QTR" + ((testDate.getMonthValue() - 1) / 3 + 1);
        String dateStr = testDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String url = String.format("https://www.sec.gov/Archives/edgar/data/%s/%s/master.%s.idx", year, quarter, dateStr);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/plain")
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Daily master index status: " + response.statusCode());
    }

    private static void testForm4DocumentDownload() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://www.sec.gov/Archives/edgar/data/789019/000078901924000001/doc4.xml"))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/xml, text/xml, */*")
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Form 4 document status: " + response.statusCode());
    }
}