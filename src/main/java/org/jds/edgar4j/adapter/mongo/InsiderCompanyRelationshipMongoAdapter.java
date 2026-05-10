package org.jds.edgar4j.adapter.mongo;

import org.jds.edgar4j.port.InsiderCompanyRelationshipDataPort;
import org.jds.edgar4j.repository.insider.InsiderCompanyRelationshipRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@Component
@Profile("resource-high & !resource-low")
@RequiredArgsConstructor
public class InsiderCompanyRelationshipMongoAdapter implements InsiderCompanyRelationshipDataPort {

    @Delegate(types = InsiderCompanyRelationshipDataPort.class)
    private final InsiderCompanyRelationshipRepository repository;
}