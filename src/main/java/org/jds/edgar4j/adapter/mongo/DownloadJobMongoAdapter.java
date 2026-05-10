package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.DownloadJobDataPort;
import org.jds.edgar4j.repository.DownloadJobRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class DownloadJobMongoAdapter implements DownloadJobDataPort {

    @Delegate(types = DownloadJobDataPort.class)
    private final DownloadJobRepository repository;
}
