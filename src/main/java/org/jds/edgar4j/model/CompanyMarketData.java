package org.jds.edgar4j.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = false)
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Document(collection = "company_market_data")
public class CompanyMarketData {

    @Id
    private String id;

    @Indexed(unique = true)
    private String ticker;

    @Indexed
    private String cik;

    private Double marketCap;

    private Double currentPrice;

    private Double previousClose;

    @Builder.Default
    private String currency = "USD";

    private Instant lastUpdated;
}
