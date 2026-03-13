package org.jds.edgar4j.service;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jds.edgar4j.client.EdgarFilingsClient;
import org.jds.edgar4j.integration.SecUserAgentPolicy;
import org.jds.edgar4j.model.search.FilingResult;
import org.jds.edgar4j.model.search.FilingSearch;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchFilingsService {

    private final EdgarFilingsClient edgarFilingsClient;

    public List<FilingResult> searchFilings(String cikNo, String formType, int filingYear, List<String> wordList, String userAgent) {
        validateUserAgent(userAgent);

        List<FilingSearch> filings = edgarFilingsClient.getFilings(cikNo, formType, filingYear, userAgent);

        return filings.stream().map(filing -> {
            int wordHits = countWordHits(filing.getText(), wordList);
            return FilingResult.fromFiling(filing, wordHits);
        }).collect(Collectors.toList());
    }

    private void validateUserAgent(String userAgent) {
        if (!SecUserAgentPolicy.isValid(userAgent)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, SecUserAgentPolicy.guidance());
        }
    }

    private int countWordHits(String text, List<String> wordList) {
        return wordList.stream().map(word -> countWordOccurrences(text, word)).mapToInt(Integer::intValue).sum();
    }

    private int countWordOccurrences(String text, String word) {
        Pattern pattern = Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE);
        return (int) pattern.matcher(text).results().count();
    }
}
