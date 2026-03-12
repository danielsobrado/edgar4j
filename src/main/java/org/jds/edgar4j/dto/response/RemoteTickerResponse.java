package org.jds.edgar4j.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RemoteTickerResponse {

    private String cik;
    private String ticker;
    private String name;
    private String exchange;
}

