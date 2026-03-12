package org.jds.edgar4j.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jds.edgar4j.model.Sp500Constituent;

public interface Sp500Service {

    List<Sp500Constituent> syncFromWikipedia();

    List<Sp500Constituent> getAll();

    Set<String> getAllTickers();

    boolean isSp500(String ticker);

    Optional<Sp500Constituent> findByTicker(String ticker);

    long count();
}
