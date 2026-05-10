package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.Sp500ConstituentDataPort;
import org.jds.edgar4j.repository.Sp500ConstituentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high & !resource-low")
@RequiredArgsConstructor
public class Sp500ConstituentMongoAdapter implements Sp500ConstituentDataPort {

    @Delegate(types = Sp500ConstituentDataPort.class)
    private final Sp500ConstituentRepository repository;
}