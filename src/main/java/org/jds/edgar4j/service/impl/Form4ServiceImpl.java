package org.jds.edgar4j.service.impl;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.InsiderForm;
import org.jds.edgar4j.service.Form4Service;
import org.jds.edgar4j.service.InsiderFormService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for downloading and parsing SEC Form 4 filings
 *
 * This class is deprecated in favor of InsiderFormService which supports Forms 3, 4, and 5.
 * This wrapper maintains backwards compatibility for existing code.
 *
 * @author J. Daniel Sobrado
 * @version 3.0
 * @since 2025-11-05
 * @deprecated Use {@link InsiderFormService} instead
 */
@Slf4j
@Service
@Deprecated
public class Form4ServiceImpl implements Form4Service {

        @Autowired
        private InsiderFormService insiderFormService;

        @Override
        public CompletableFuture<HttpResponse<String>> downloadForm4(String cik, String accessionNumber, String primaryDocument) {
                log.warn("Form4Service.downloadForm4() is deprecated. Use InsiderFormService.downloadInsiderForm() instead.");
                return insiderFormService.downloadInsiderForm(cik, accessionNumber, primaryDocument);
        }

        @Override
        public Form4 parseForm4(String raw) {
                log.warn("Form4Service.parseForm4() is deprecated. Use InsiderFormService.parseInsiderForm() instead.");
                InsiderForm insiderForm = insiderFormService.parseInsiderForm(raw, "4");
                return Form4.from(insiderForm);
        }
}
