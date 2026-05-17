package org.jds.edgar4j.port;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.DividendAlertResolution;

public interface DividendAlertResolutionDataPort extends BaseDocumentDataPort<DividendAlertResolution> {

    Optional<DividendAlertResolution> findByResolutionKey(String resolutionKey);

    List<DividendAlertResolution> findByCik(String cik);
}
