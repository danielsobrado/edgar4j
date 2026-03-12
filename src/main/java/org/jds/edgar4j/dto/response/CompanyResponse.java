package org.jds.edgar4j.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyResponse {

    private String id;
    private String name;
    private String ticker;
    private String cik;
    private String sic;
    private String sicDescription;
    private String entityType;
    private String stateOfIncorporation;
    private String stateOfIncorporationDescription;
    private Long fiscalYearEnd;
    private String ein;
    private String description;
    private String website;
    private String investorWebsite;
    private String category;
    private List<String> tickers;
    private List<String> exchanges;
    private AddressResponse businessAddress;
    private AddressResponse mailingAddress;
    private Long filingCount;
    private boolean hasInsiderTransactions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressResponse {
        private String street1;
        private String street2;
        private String city;
        private String stateOrCountry;
        private String zipCode;
    }
}
