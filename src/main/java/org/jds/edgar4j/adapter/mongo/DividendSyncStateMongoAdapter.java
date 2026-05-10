package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.DividendSyncStateDataPort;
import org.jds.edgar4j.repository.DividendSyncStateRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class DividendSyncStateMongoAdapter implements DividendSyncStateDataPort {

    @Delegate(types = DividendSyncStateDataPort.class)
    private final DividendSyncStateRepository repository;
}