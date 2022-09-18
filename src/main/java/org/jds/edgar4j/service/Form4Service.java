package org.jds.edgar4j.service;

import org.jds.edgar4j.entity.Form4;
import org.springframework.stereotype.Service;

/**
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2022-09-18
 */
@Service
public interface Form4Service {

    Form4 downloadForm4(String cik, String accessionNumber, String primaryDocument);
    void parseForm4();

}
