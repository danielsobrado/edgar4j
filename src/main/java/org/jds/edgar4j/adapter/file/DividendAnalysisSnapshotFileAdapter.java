package org.jds.edgar4j.adapter.file;

import java.util.Optional;

import org.jds.edgar4j.model.DividendAnalysisSnapshot;
import org.jds.edgar4j.port.DividendAnalysisSnapshotDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class DividendAnalysisSnapshotFileAdapter extends AbstractFileDataPort<DividendAnalysisSnapshot>
        implements DividendAnalysisSnapshotDataPort {

    private static final String INDEX_CIK = "cik";
    private static final String INDEX_TICKER = "ticker";

    public DividendAnalysisSnapshotFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "dividend_analysis_snapshots",
                DividendAnalysisSnapshot.class,
                FileFormat.JSON,
                DividendAnalysisSnapshot::getId,
                DividendAnalysisSnapshot::setId));
        registerExactIndex(INDEX_CIK, DividendAnalysisSnapshot::getCik);
        registerIgnoreCaseIndex(INDEX_TICKER, DividendAnalysisSnapshot::getTicker);
    }

    @Override
    public Optional<DividendAnalysisSnapshot> findByCik(String cik) {
        return findFirstByIndex(INDEX_CIK, cik);
    }

    @Override
    public Optional<DividendAnalysisSnapshot> findByTickerIgnoreCase(String ticker) {
        return findFirstByIndex(INDEX_TICKER, ticker);
    }
}
