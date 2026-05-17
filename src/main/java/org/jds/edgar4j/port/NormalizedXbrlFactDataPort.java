package org.jds.edgar4j.port;

import java.util.List;

import org.jds.edgar4j.model.NormalizedXbrlFact;

public interface NormalizedXbrlFactDataPort extends BaseDocumentDataPort<NormalizedXbrlFact> {

    List<NormalizedXbrlFact> findByCik(String cik);

    List<NormalizedXbrlFact> findByCikAndStandardConceptAndCurrentBestTrueOrderByPeriodEndDesc(
            String cik,
            String standardConcept);
}
