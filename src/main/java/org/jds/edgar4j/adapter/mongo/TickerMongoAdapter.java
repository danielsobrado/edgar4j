package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.TickerDataPort;
import org.jds.edgar4j.repository.TickerRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class TickerMongoAdapter implements TickerDataPort {

    @Delegate(types = TickerDataPort.class)
    private final TickerRepository repository;
}