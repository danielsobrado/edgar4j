package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.SearchHistoryDataPort;
import org.jds.edgar4j.repository.SearchHistoryRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class SearchHistoryMongoAdapter implements SearchHistoryDataPort {

    @Delegate(types = SearchHistoryDataPort.class)
    private final SearchHistoryRepository repository;
}