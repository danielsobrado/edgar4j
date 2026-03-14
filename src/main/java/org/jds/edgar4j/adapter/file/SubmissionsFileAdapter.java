package org.jds.edgar4j.adapter.file;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Submissions;
import org.jds.edgar4j.port.SubmissionsDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class SubmissionsFileAdapter extends AbstractFileDataPort<Submissions> implements SubmissionsDataPort {

    private static final String INDEX_CIK = "cik";
    private static final String INDEX_TICKER = "ticker";

    public SubmissionsFileAdapter(FileStorageEngine storageEngine) {
        super(storageEngine.registerCollection(
                "submissions",
                Submissions.class,
                FileFormat.JSONL,
                Submissions::getId,
                Submissions::setId));
        registerExactIndex(INDEX_CIK, Submissions::getCik);
        registerMultiValueIgnoreCaseIndex(INDEX_TICKER, value -> value.getTickers() == null ? List.of() : value.getTickers());
    }

    @Override
    public Optional<Submissions> findByCik(String cik) {
        return findFirstByIndex(INDEX_CIK, cik);
    }

    @Override
    public Page<Submissions> searchByCompanyNameOrCik(String searchTerm, Pageable pageable) {
        return findMatching(value ->
                (value.getCompanyName() != null && containsIgnoreCase(value.getCompanyName(), searchTerm))
                        || (searchTerm != null && searchTerm.equals(value.getCik())), pageable);
    }

    @Override
    public List<Submissions> findByTickersContaining(String ticker) {
        return findAllByIndex(INDEX_TICKER, ticker);
    }
}
