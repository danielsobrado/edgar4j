package org.jds.edgar4j.port;

import java.util.Optional;

import org.jds.edgar4j.model.DividendAnalysisSnapshot;

public interface DividendAnalysisSnapshotDataPort extends BaseDocumentDataPort<DividendAnalysisSnapshot> {

    Optional<DividendAnalysisSnapshot> findByCik(String cik);

    Optional<DividendAnalysisSnapshot> findByTickerIgnoreCase(String ticker);
}
