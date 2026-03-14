package org.jds.edgar4j.port;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Sp500Constituent;

public interface Sp500ConstituentDataPort extends BaseDocumentDataPort<Sp500Constituent> {

    Optional<Sp500Constituent> findByTickerIgnoreCase(String ticker);

    boolean existsByTickerIgnoreCase(String ticker);

    List<Sp500Constituent> findAllByOrderByTickerAsc();
}
