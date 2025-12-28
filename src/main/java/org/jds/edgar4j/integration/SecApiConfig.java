package org.jds.edgar4j.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;

@Getter
@Configuration
public class SecApiConfig {

    @Value("${edgar4j.urls.baseSecUrl}")
    private String baseSecUrl;

    @Value("${edgar4j.urls.baseDataSecUrl}")
    private String baseDataSecUrl;

    @Value("${edgar4j.urls.edgarDataArchivesUrl}")
    private String edgarDataArchivesUrl;

    @Value("${edgar4j.urls.submissionsUrl}")
    private String submissionsUrl;

    @Value("${edgar4j.urls.submissionsCIKUrl}")
    private String submissionsCIKUrl;

    @Value("${edgar4j.urls.bulkSubmissionsFileUrl}")
    private String bulkSubmissionsFileUrl;

    @Value("${edgar4j.urls.bulkCompanyFactsFileUrl}")
    private String bulkCompanyFactsFileUrl;

    @Value("${edgar4j.urls.companyTickersUrl}")
    private String companyTickersUrl;

    @Value("${edgar4j.urls.companyTickersExchangesUrl}")
    private String companyTickersExchangesUrl;

    @Value("${edgar4j.urls.companyTickersMFsUrl}")
    private String companyTickersMFsUrl;

    public String formatCik(String cik) {
        try {
            long cikLong = Long.parseLong(cik);
            return String.format("%010d", cikLong);
        } catch (NumberFormatException e) {
            return cik;
        }
    }

    public String getSubmissionUrl(String cik) {
        return submissionsCIKUrl + formatCik(cik) + ".json";
    }

    public String getForm4Url(String cik, String accessionNumber, String primaryDocument) {
        String cleanedAccession = accessionNumber.replace("-", "");
        return String.format("%s/%s/%s/%s", edgarDataArchivesUrl, cik, cleanedAccession, primaryDocument);
    }

    /**
     * Gets the URL for any EDGAR filing document.
     */
    public String getFilingUrl(String cik, String accessionNumber, String document) {
        String cleanedAccession = accessionNumber.replace("-", "");
        return String.format("%s/%s/%s/%s", edgarDataArchivesUrl, formatCik(cik), cleanedAccession, document);
    }
}
