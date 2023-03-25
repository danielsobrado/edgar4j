package org.jds.edgar4j.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MasterIndexEntry {
    private String cik;
    private String companyName;
    private String formType;
    private LocalDate dateFiled;
    private String edgarLink;
}
