package org.jds.edgar4j.model.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FilingResult {

    private String message;
    private List<FilingSearch> filings;

    public static FilingResult fromFiling(FilingSearch filing, int wordHits) {
        filing.setNwordHits(wordHits);
        return new FilingResult(null, List.of(filing));
    }
}
