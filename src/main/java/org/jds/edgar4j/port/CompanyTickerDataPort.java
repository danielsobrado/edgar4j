package org.jds.edgar4j.port;

import java.util.Optional;

import org.jds.edgar4j.model.CompanyTicker;

public interface CompanyTickerDataPort extends BaseDocumentDataPort<CompanyTicker> {

    Optional<CompanyTicker> findByTickerIgnoreCase(String ticker);

    Optional<CompanyTicker> findFirstByCikStr(Long cikStr);
}
