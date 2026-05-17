package org.jds.edgar4j.adapter.file;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.DividendAlertResolution;
import org.jds.edgar4j.port.DividendAlertResolutionDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class DividendAlertResolutionFileAdapter extends AbstractFileDataPort<DividendAlertResolution>
        implements DividendAlertResolutionDataPort {

    private static final String INDEX_RESOLUTION_KEY = "resolutionKey";
    private static final String INDEX_CIK = "cik";

    public DividendAlertResolutionFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "dividend_alert_resolutions",
                DividendAlertResolution.class,
                FileFormat.JSON,
                DividendAlertResolution::getId,
                DividendAlertResolution::setId));
        registerExactIndex(INDEX_RESOLUTION_KEY, DividendAlertResolution::getResolutionKey);
        registerExactIndex(INDEX_CIK, DividendAlertResolution::getCik);
    }

    @Override
    public Optional<DividendAlertResolution> findByResolutionKey(String resolutionKey) {
        return findFirstByIndex(INDEX_RESOLUTION_KEY, resolutionKey);
    }

    @Override
    public List<DividendAlertResolution> findByCik(String cik) {
        return findAllByIndex(INDEX_CIK, cik);
    }
}
