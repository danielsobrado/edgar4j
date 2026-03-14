package org.jds.edgar4j.adapter.mongo;

import org.jds.edgar4j.port.TransactionTypeDataPort;
import org.jds.edgar4j.repository.insider.TransactionTypeRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@Component
@Profile("resource-high & !resource-low")
@RequiredArgsConstructor
public class TransactionTypeMongoAdapter implements TransactionTypeDataPort {

    @Delegate(types = TransactionTypeDataPort.class)
    private final TransactionTypeRepository repository;
}