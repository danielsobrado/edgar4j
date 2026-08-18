package org.jds.edgar4j.service.impl;

import java.net.URI;

import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.service.WorkerSourceResourcePolicy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
public class StrictWorkerSourceResourcePolicy implements WorkerSourceResourcePolicy {

    private static final int HTTPS_PORT = 443;

    private final WorkerSourceResourcePolicyImpl delegate;

    public StrictWorkerSourceResourcePolicy(WorkerSourceResourcePolicyImpl delegate) {
        this.delegate = delegate;
    }

    @Override
    public void validate(WorkerTask task) {
        delegate.validate(task);
        URI uri = URI.create(task.getSourceUrl());
        if (uri.getPort() != -1 && uri.getPort() != HTTPS_PORT) {
            throw new IllegalArgumentException("Worker source URL must use the standard HTTPS port");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Worker source URL cannot contain user information");
        }
        if (uri.getFragment() != null) {
            throw new IllegalArgumentException("Worker source URL cannot contain a fragment");
        }
    }
}
