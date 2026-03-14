package org.jds.edgar4j.service;

import org.jds.edgar4j.dto.response.DividendQualityResponse;

public interface DividendQualityService {

    DividendQualityResponse assess(String tickerOrCik);
}
