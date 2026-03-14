package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.Form4DataPort;
import org.jds.edgar4j.repository.Form4Repository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high & !resource-low")
@RequiredArgsConstructor
public class Form4MongoAdapter implements Form4DataPort {

    @Delegate(types = Form4DataPort.class)
    private final Form4Repository repository;
}