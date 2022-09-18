package org.jds.edgar4j.service;

import org.springframework.stereotype.Service;

/**
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2022-09-18
 */
@Service
public interface DownloadSubmissionsService {

    void downloadSubmissions(String cik);

}
