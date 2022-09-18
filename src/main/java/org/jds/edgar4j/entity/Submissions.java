package org.jds.edgar4j.entity;

import java.util.Date;

import org.springframework.data.annotation.Id;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;


/**
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2022-09-18
 */
@EqualsAndHashCode(callSuper=false)
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Submissions {

    @Id
    private String id;

    private String name;
    private String companyName;
    private String cik;
    private String entityType;
    private String sic;
    private String sicDescription;
    private boolean insiderTransactionForOwnerExists;
    private boolean insiderTransactionForIssuerExists;
    private Ticker[] tickers;
    private Exchange[] exchanges;
    private String ein;
    private String description;
    private String website;
    private String investorWebsite;
    private String category;
    private Long fiscalYearEnd;
    private String stateOfIncorporation;
    private String stateOfIncorporationDescription;
    private String fileName;
    private Long fillingCount;
    private Date fillingFrom;
    private Date fillingTo;

}
