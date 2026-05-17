package org.jds.edgar4j.adapter.file;

import java.util.Comparator;
import java.util.List;

import org.jds.edgar4j.model.NormalizedXbrlFact;
import org.jds.edgar4j.port.NormalizedXbrlFactDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class NormalizedXbrlFactFileAdapter extends AbstractFileDataPort<NormalizedXbrlFact>
        implements NormalizedXbrlFactDataPort {

    private static final String INDEX_CIK = "cik";

    public NormalizedXbrlFactFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "xbrl_facts",
                NormalizedXbrlFact.class,
                FileFormat.JSON,
                NormalizedXbrlFact::getId,
                NormalizedXbrlFact::setId));
        registerExactIndex(INDEX_CIK, NormalizedXbrlFact::getCik);
    }

    @Override
    public List<NormalizedXbrlFact> findByCik(String cik) {
        return findAllByIndex(INDEX_CIK, cik);
    }

    @Override
    public List<NormalizedXbrlFact> findByCikAndStandardConceptAndCurrentBestTrueOrderByPeriodEndDesc(
            String cik,
            String standardConcept) {
        return findMatching(fact -> cik != null
                        && cik.equals(fact.getCik())
                        && standardConcept != null
                        && standardConcept.equals(fact.getStandardConcept())
                        && fact.isCurrentBest())
                .stream()
                .sorted(Comparator.comparing(
                        NormalizedXbrlFact::getPeriodEnd,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
