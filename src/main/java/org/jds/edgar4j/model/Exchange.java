package org.jds.edgar4j.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
@Document(collection = "exchanges")
public class Exchange {

    @Id
    private String id;

    private String name;

    private String code;

    private String country;

    // NASDAQ = "Nasdaq"
    // OTC = "OTC"
    // CBOE = "CBOE"
    // NYSE = "NYSE"
    // NONE = ""
}

