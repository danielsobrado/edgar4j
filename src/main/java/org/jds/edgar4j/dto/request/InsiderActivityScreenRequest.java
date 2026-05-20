package org.jds.edgar4j.dto.request;

import java.time.LocalDate;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsiderActivityScreenRequest {

    private String preset;
    private String view;
    private String side;
    private Set<String> transactionCodes;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String symbol;
    private Double minPrice;
    private Double minShares;
    private Double minTotalAmount;
    private Integer minInsiderCount;
    private String insiderTitle;
    private String sortBy;
    private String sortDir;
    private int page;
    private int size;
}
