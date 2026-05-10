package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.Form3DataPort;
import org.jds.edgar4j.repository.Form3Repository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high & !resource-low")
@RequiredArgsConstructor
public class Form3MongoAdapter implements Form3DataPort {

    @Delegate(types = Form3DataPort.class)
    private final Form3Repository repository;
}