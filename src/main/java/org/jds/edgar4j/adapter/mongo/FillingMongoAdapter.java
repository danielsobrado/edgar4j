package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.repository.FillingRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class FillingMongoAdapter implements FillingDataPort {

    @Delegate(types = FillingDataPort.class)
    private final FillingRepository repository;
}