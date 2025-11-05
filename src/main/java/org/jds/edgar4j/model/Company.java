package org.jds.edgar4j.model;

import org.springframework.data.annotation.Id;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;


/**
 * Company model with industry classification
 *
 * @author J. Daniel Sobrado
 * @version 1.1
 * @since 2025-11-05
 */
@EqualsAndHashCode(callSuper=false)
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Company {

    @Id
    private String id;

    private String name;
    private String description;
    private String ticker;
    private String cik;
    private String taxonomy;

    /** SIC (Standard Industrial Classification) code */
    private String sic;

    /** Industry classification description (from SIC code) */
    private String industry;

    /** Entity type (e.g., operating company, shell company) */
    private String entityType;

    /** State/country of incorporation */
    private String stateOfIncorporation;

}
