package org.jds.edgar4j.service;

import org.jds.edgar4j.model.WorkerFailureCode;

public interface WorkerSourceFailureClassifier {

    WorkerFailureCode classify(RuntimeException failure);
}
