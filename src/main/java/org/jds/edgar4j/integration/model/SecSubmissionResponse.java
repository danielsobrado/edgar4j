package org.jds.edgar4j.integration.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecSubmissionResponse {

    private String cik;
    private String entityType;
    private String sic;
    private String sicDescription;
    private int insiderTransactionForOwnerExists;
    private int insiderTransactionForIssuerExists;
    private String name;
    private List<String> tickers;
    private List<String> exchanges;
    private String ein;
    private String description;
    private String website;
    private String investorWebsite;
    private String category;
    private String fiscalYearEnd;
    private String stateOfIncorporation;
    private String stateOfIncorporationDescription;
    private Addresses addresses;
    private String phone;
    private String flags;
    private Filings filings;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Addresses {
        private Address mailing;
        private Address business;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Address {
        private String street1;
        private String street2;
        private String city;
        private String stateOrCountry;
        private String zipCode;
        private String stateOrCountryDescription;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Filings {
        private Recent recent;
        private List<FilesEntry> files;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Recent {
        private List<String> accessionNumber;
        private List<String> filingDate;
        private List<String> reportDate;
        private List<String> acceptanceDateTime;
        private List<String> act;
        private List<String> form;
        private List<String> fileNumber;
        private List<String> filmNumber;
        private List<String> items;
        private List<Integer> size;
        @JsonProperty("isXBRL")
        private List<Integer> isXBRL;
        @JsonProperty("isInlineXBRL")
        private List<Integer> isInlineXBRL;
        private List<String> primaryDocument;
        private List<String> primaryDocDescription;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FilesEntry {
        private String name;
        private int filingCount;
        private String filingFrom;
        private String filingTo;
    }
}
