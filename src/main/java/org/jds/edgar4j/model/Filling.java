package org.jds.edgar4j.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;
import org.springframework.data.annotation.Id;

import java.util.Date;


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
public class Filling {

    @Id
    private String id;
  
    private String company;
    private FormType fillingType;
    private String cik;
    private String sic;
    private String fiscalYearEnd;
    private FormType formType;
    private Date fillingDate;
    private Date reportDate;
    private String url;
    private String accessionNumber;
    private String fileNumber;
    private String filmNumber;
    private String items;
    private boolean isXBRL;
    private boolean isInlineXBRL;
    private String primaryDocument;
    private String primaryDocDescription;
    
    

}
