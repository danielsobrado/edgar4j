package org.jds.edgar4j.model.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FilingSearch {

    private String cik;
    private String companyName;
    private String formType;
    private LocalDate filingDate;
    private String accessionNumber;
    private int nwordHits;
    private String text;

    public String getText() {
        return text;
    }
}
