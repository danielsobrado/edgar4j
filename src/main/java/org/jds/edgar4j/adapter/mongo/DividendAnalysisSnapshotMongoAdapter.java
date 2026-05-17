package org.jds.edgar4j.adapter.mongo;

import org.jds.edgar4j.port.DividendAnalysisSnapshotDataPort;
import org.jds.edgar4j.repository.DividendAnalysisSnapshotRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class DividendAnalysisSnapshotMongoAdapter implements DividendAnalysisSnapshotDataPort {

    @Delegate(types = DividendAnalysisSnapshotDataPort.class)
    private final DividendAnalysisSnapshotRepository repository;
}
