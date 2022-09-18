package org.jds.edgar4j.entity;

import java.util.Date;

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
public class Form4 {

    private String securityTitle;
    private Date transactionDate;
    private float transactionValue;
    private Date expirationDate;
    private String boughtSold;
    private float exercisePrice;
    private float exerciseShares;
    private String tickerElement;
    private String tradingSymbol;
    private String owner;
    private String rptOwnerName;
    private boolean isDirector;
    private boolean isOfficer;
    private boolean isTenOwner;
    private boolean isOther;
    
}
