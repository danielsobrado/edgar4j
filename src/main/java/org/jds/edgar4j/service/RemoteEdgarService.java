package org.jds.edgar4j.service;

import java.util.List;

import org.jds.edgar4j.dto.response.RemoteSubmissionResponse;
import org.jds.edgar4j.dto.response.RemoteTickerResponse;

public interface RemoteEdgarService {

    List<RemoteTickerResponse> getRemoteTickers(String source, String search, int limit);

    RemoteSubmissionResponse getRemoteSubmission(String cik, int filingsLimit);
}

