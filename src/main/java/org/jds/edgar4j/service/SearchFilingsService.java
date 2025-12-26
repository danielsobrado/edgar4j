package org.jds.edgar4j.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.client.EdgarFilingsClient;
import org.jds.edgar4j.model.search.FilingResult;
import org.jds.edgar4j.model.search.FilingSearch;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;
import java.util.List;
import java.util.stream.Collectors;

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
        if (userAgent.isEmpty() || isInvalidUserAgent(userAgent)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please provide a valid User Agent. Visit https://www.sec.gov/os/accessing-edgar-data for more information");
        }
    }

    private boolean isInvalidUserAgent(String userAgent) {
        // Matches a valid User-Agent string format
        Pattern pattern = Pattern.compile("^[A-Za-z0-9\\-_.]+/[A-Za-z0-9\\-_.]+(\\s*\\([A-Za-z0-9\\-_.]+;\\s*[A-Za-z0-9\\-_.]+(;\\s*[A-Za-z0-9\\-_.]+)*\\))?$", Pattern.CASE_INSENSITIVE);
        return !pattern.matcher(userAgent).matches();
    }

    private int countWordHits(String text, List<String> wordList) {
        return wordList.stream().map(word -> countWordOccurrences(text, word)).mapToInt(Integer::intValue).sum();
    }

    private int countWordOccurrences(String text, String word) {
        Pattern pattern = Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE);
        return (int) pattern.matcher(text).results().count();
    }
}
