package org.jds.edgar4j.service.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.jds.edgar4j.exception.WorkerCoordinatorException;
import org.jds.edgar4j.model.WorkerSource;
import org.jds.edgar4j.model.WorkerTask;
import org.jds.edgar4j.properties.DistributedWorkerProperties;
import org.jds.edgar4j.service.WorkerSourceResourcePolicy;
import org.springframework.stereotype.Service;

@Service
public class WorkerSourceResourcePolicyImpl implements WorkerSourceResourcePolicy {

    private static final String ERROR_CODE = "WORKER_SOURCE_RESOURCE_REJECTED";

    private final DistributedWorkerProperties properties;

    public WorkerSourceResourcePolicyImpl(DistributedWorkerProperties properties) {
        this.properties = properties;
    }

    @Override
    public void validate(WorkerTask task) {
        if (task == null || task.getSource() != WorkerSource.SEC_EDGAR) {
            reject("Unsupported worker source");
        }
        if (task.getSourceUrl() == null || task.getSourceUrl().isBlank()) {
            reject("Worker source URL is required");
        }

        URI uri;
        try {
            uri = new URI(task.getSourceUrl());
        } catch (URISyntaxException e) {
            throw new WorkerCoordinatorException("Invalid worker source URL", ERROR_CODE, e);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            reject("Worker source URL is not allowed");
        }

        Set<String> allowedHosts = properties.getSourcePolicy().getAllowedHosts().stream()
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        if (!allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            reject("Worker source host is not allowed");
        }
    }

    private static void reject(String message) {
        throw new WorkerCoordinatorException(message, ERROR_CODE);
    }
}
