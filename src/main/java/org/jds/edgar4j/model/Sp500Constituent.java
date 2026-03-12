package org.jds.edgar4j.model;

import java.time.Instant;
import java.time.LocalDate;

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
@Document(collection = "sp500_constituents")
public class Sp500Constituent {

    @Id
    private String id;

    @Indexed(unique = true)
    private String ticker;

    private String companyName;

    @Indexed
    private String cik;

    private String sector;

    private String subIndustry;

    private LocalDate dateAdded;

    private Instant lastUpdated;
}
