package org.jds.edgar4j.adapter.file;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Ticker;
import org.jds.edgar4j.port.TickerDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class TickerFileAdapter extends AbstractFileDataPort<Ticker> implements TickerDataPort {

    public TickerFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "tickers",
                Ticker.class,
                FileFormat.JSON,
                Ticker::getId,
                Ticker::setId));
    }

    @Override
    public Optional<Ticker> findByCode(String code) {
        return findFirst(value -> equalsIgnoreCase(value.getCode(), code));
    }

    @Override
    public Optional<Ticker> findByCik(String cik) {
        return findFirst(value -> cik != null && cik.equals(value.getCik()));
    }

    @Override
    public List<Ticker> findByCodeIn(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return findMatching(value -> codes.stream().anyMatch(code -> equalsIgnoreCase(value.getCode(), code)));
    }
}
