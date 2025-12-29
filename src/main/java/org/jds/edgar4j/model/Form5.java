package org.jds.edgar4j.model;

import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Form 5 - Annual Statement of Beneficial Ownership.
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Document(collection = "form5")
@CompoundIndexes({
    @CompoundIndex(name = "cik_report_idx", def = "{'cik': 1, 'periodOfReport': -1}"),
    @CompoundIndex(name = "symbol_report_idx", def = "{'tradingSymbol': 1, 'periodOfReport': -1}"),
    @CompoundIndex(name = "owner_report_idx", def = "{'rptOwnerName': 1, 'periodOfReport': -1}")
})
public class Form5 {

    @Id
    private String id;

    @Indexed(unique = true)
    private String accessionNumber;

    private String documentType;

    private Date periodOfReport;

    private Date filedDate;

    // Issuer information
    @Indexed
    private String cik;

    private String issuerName;

    @Indexed
    private String tradingSymbol;

    // Reporting owner information
    private String rptOwnerCik;

    @Indexed
    private String rptOwnerName;

    private String officerTitle;

    private boolean isDirector;

    private boolean isOfficer;

    private boolean isTenPercentOwner;

    private boolean isOther;

    /**
     * Derived owner type for consistency.
     * Values: Director, Officer, 10% Owner, Other, Unknown
     */
    private String ownerType;

    // Transactions (non-derivative + derivative)
    private List<Form4Transaction> transactions;

    // Holdings (non-derivative + derivative)
    private List<Form4Transaction> holdings;

    private Date createdAt;

    private Date updatedAt;
}

