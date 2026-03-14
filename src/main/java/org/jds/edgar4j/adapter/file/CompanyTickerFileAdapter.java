package org.jds.edgar4j.adapter.file;

import java.util.Optional;

import org.jds.edgar4j.model.CompanyTicker;
import org.jds.edgar4j.port.CompanyTickerDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class CompanyTickerFileAdapter extends AbstractFileDataPort<CompanyTicker> implements CompanyTickerDataPort {

    private static final String INDEX_TICKER = "ticker";
    private static final String INDEX_CIK_STR = "cikStr";

    public CompanyTickerFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "company_tickers",
                CompanyTicker.class,
                FileFormat.JSON,
                CompanyTicker::getId,
                CompanyTicker::setId));
        registerIgnoreCaseIndex(INDEX_TICKER, CompanyTicker::getTicker);
        registerExactIndex(INDEX_CIK_STR, CompanyTicker::getCikStr);
    }

    @Override
    public Optional<CompanyTicker> findByTickerIgnoreCase(String ticker) {
        return findFirstByIndex(INDEX_TICKER, ticker);
    }

    @Override
    public Optional<CompanyTicker> findFirstByCikStr(Long cikStr) {
        return findFirstByIndex(INDEX_CIK_STR, cikStr);
    }
}
