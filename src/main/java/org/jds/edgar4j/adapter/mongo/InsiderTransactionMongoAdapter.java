package org.jds.edgar4j.adapter.mongo;

import org.jds.edgar4j.port.InsiderTransactionDataPort;
import org.jds.edgar4j.repository.insider.InsiderTransactionRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class InsiderTransactionMongoAdapter implements InsiderTransactionDataPort {

    @Delegate(types = InsiderTransactionDataPort.class)
    private final InsiderTransactionRepository repository;
}
