package org.jds.edgar4j.adapter.mongo;

import org.jds.edgar4j.port.NormalizedXbrlFactDataPort;
import org.jds.edgar4j.repository.NormalizedXbrlFactRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class NormalizedXbrlFactMongoAdapter implements NormalizedXbrlFactDataPort {

    @Delegate(types = NormalizedXbrlFactDataPort.class)
    private final NormalizedXbrlFactRepository repository;
}
