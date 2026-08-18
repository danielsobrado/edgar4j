package org.jds.edgar4j.service.impl;

import java.net.http.HttpTimeoutException;
import java.util.Locale;

import org.jds.edgar4j.exception.SecApiException;
import org.jds.edgar4j.model.WorkerFailureCode;
import org.jds.edgar4j.service.WorkerSourceFailureClassifier;
import org.springframework.stereotype.Service;

@Service
public class WorkerSourceFailureClassifierImpl implements WorkerSourceFailureClassifier {

    @Override
    public WorkerFailureCode classify(RuntimeException failure) {
        if (failure instanceof IllegalArgumentException) {
            return WorkerFailureCode.SOURCE_REJECTED;
        }
        if (failure instanceof SecApiException) {
            String message = failure.getMessage() == null
                    ? ""
                    : failure.getMessage().toLowerCase(Locale.ROOT);
            if (message.startsWith("resource not found:")) {
                return WorkerFailureCode.SOURCE_NOT_FOUND;
            }
            if (message.contains("rate limit") || message.contains("http 429")) {
                return WorkerFailureCode.SOURCE_RATE_LIMITED;
            }
            if (message.contains("timeout") || message.contains("interrupted") || hasTimeoutCause(failure)) {
                return WorkerFailureCode.SOURCE_TIMEOUT;
            }
        }
        return WorkerFailureCode.INTERNAL_ERROR;
    }

    private static boolean hasTimeoutCause(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
