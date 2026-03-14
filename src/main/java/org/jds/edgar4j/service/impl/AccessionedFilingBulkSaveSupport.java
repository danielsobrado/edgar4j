package org.jds.edgar4j.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

final class AccessionedFilingBulkSaveSupport {

    private AccessionedFilingBulkSaveSupport() {
    }

    static <T> void alignExistingIds(
            List<T> filings,
            Function<T, String> accessionGetter,
            Function<String, Optional<T>> existingLookup,
            BiConsumer<T, T> mergeExisting) {
        if (filings == null || filings.isEmpty()) {
            return;
        }

        Map<String, T> existingByAccession = new HashMap<>();
        for (T filing : filings) {
            String accessionNumber = accessionGetter.apply(filing);
            if (accessionNumber == null || accessionNumber.isBlank() || existingByAccession.containsKey(accessionNumber)) {
                continue;
            }
            existingLookup.apply(accessionNumber).ifPresent(existing -> existingByAccession.put(accessionNumber, existing));
        }

        for (T filing : filings) {
            String accessionNumber = accessionGetter.apply(filing);
            if (accessionNumber == null || accessionNumber.isBlank()) {
                continue;
            }
            T existing = existingByAccession.get(accessionNumber);
            if (existing != null) {
                mergeExisting.accept(filing, existing);
            }
        }
    }
}