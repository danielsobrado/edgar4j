package org.jds.edgar4j.constants;

import java.util.regex.Pattern;

public final class WorkerStorageConstants {

    public static final String ARTIFACT_ROOT_DIRECTORY = "worker-artifacts";
    public static final String STAGING_DIRECTORY = "staging";
    public static final String VERIFIED_DIRECTORY = "verified";
    public static final String STAGING_SUFFIX = ".part";
    public static final String SHA256_ALGORITHM = "SHA-256";
    public static final Pattern SHA256_PATTERN = Pattern.compile("^[a-f0-9]{64}$");

    private WorkerStorageConstants() {
    }
}
