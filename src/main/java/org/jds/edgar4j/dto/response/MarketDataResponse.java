package org.jds.edgar4j.dto.response;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataResponse {

    private String ticker;
    private String provider;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<PriceBar> prices;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceBar {
        private LocalDate date;
        private double open;
        private double high;
        private double low;
        private double close;
        private double volume;
    }
}
