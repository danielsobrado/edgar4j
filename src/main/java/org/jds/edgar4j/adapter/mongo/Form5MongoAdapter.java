package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.Form5DataPort;
import org.jds.edgar4j.repository.Form5Repository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class Form5MongoAdapter implements Form5DataPort {

    @Delegate(types = Form5DataPort.class)
    private final Form5Repository repository;
}
