package org.jds.edgar4j.model;

import java.time.Instant;
import java.time.LocalDate;

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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Document(collection = "political_trades")
@CompoundIndexes({
    @CompoundIndex(name = "political_trade_ticker_disclosure_idx", def = "{'ticker': 1, 'disclosureDate': -1}"),
    @CompoundIndex(name = "political_trade_politician_disclosure_idx", def = "{'politicianName': 1, 'disclosureDate': -1}"),
    @CompoundIndex(name = "political_trade_asset_disclosure_idx", def = "{'assetType': 1, 'disclosureDate': -1}")
})
public class PoliticalTrade {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sourceTradeId;

    @Indexed
    private String politicianName;

    private String party;

    private String chamber;

    private String state;

    private String issuerName;

    @Indexed
    private String ticker;

    @Indexed
    private LocalDate disclosureDate;

    @Indexed
    private LocalDate tradedDate;

    private Integer filedAfterDays;

    private String owner;

    @Indexed
    private String transactionType;

    private String amountLabel;

    private Double amountMin;

    private Double amountMax;

    private Double price;

    @Indexed
    private String assetType;

    private String sourceTradeUrl;

    @Indexed
    private String source;

    private Instant firstSeenAt;

    private Instant updatedAt;
}
