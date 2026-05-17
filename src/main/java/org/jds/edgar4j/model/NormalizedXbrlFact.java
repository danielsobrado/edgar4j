package org.jds.edgar4j.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

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
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = false)
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Document(collection = "xbrl_facts")
@CompoundIndexes({
        @CompoundIndex(
                name = "xbrl_fact_natural_key",
                def = "{'cik': 1, 'taxonomy': 1, 'tag': 1, 'unit': 1, 'periodEnd': 1, 'periodStart': 1, 'dimensionsHash': 1, 'accession': 1}",
                unique = true),
        @CompoundIndex(
                name = "xbrl_fact_standard_lookup",
                def = "{'cik': 1, 'standardConcept': 1, 'periodEnd': 1, 'currentBest': 1}")
})
public class NormalizedXbrlFact {

    @Id
    private String id;

    @Indexed
    private String cik;

    private String taxonomy;

    private String tag;

    @Indexed
    private String standardConcept;

    private String unit;

    @Indexed
    private LocalDate periodEnd;

    private LocalDate periodStart;

    private BigDecimal value;

    private String accession;

    private String form;

    private Integer fiscalYear;

    private String fiscalPeriod;

    private LocalDate filedDate;

    private String frame;

    @Builder.Default
    private String source = "API";

    private String tagVersion;

    private Boolean customTag;

    private Integer quartersCount;

    private Integer inlineXbrlPriority;

    @Builder.Default
    private String dimensionsHash = "";

    @Builder.Default
    private Map<String, String> dimensions = Map.of();

    private boolean currentBest;

    private Instant createdAt;

    private Instant updatedAt;
}
