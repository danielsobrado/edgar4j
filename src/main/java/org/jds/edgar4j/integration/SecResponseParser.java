package org.jds.edgar4j.integration;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jds.edgar4j.model.Exchange;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.FormType;
import org.jds.edgar4j.model.Submissions;
import org.jds.edgar4j.model.Ticker;
import org.jds.edgar4j.integration.model.SecSubmissionResponse;
import org.jds.edgar4j.integration.model.SecTickerExchangeResponse;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecResponseParser {

    private final ObjectMapper objectMapper;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public SecSubmissionResponse parseSubmissionResponse(String json) {
        try {
            return objectMapper.readValue(json, SecSubmissionResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse submission response", e);
            throw new RuntimeException("Failed to parse SEC submission response", e);
        }
    }

    public Submissions toSubmissions(SecSubmissionResponse response) {
        return Submissions.builder()
                .cik(response.getCik())
                .companyName(response.getName())
                .name(response.getName())
                .entityType(response.getEntityType())
                .sic(response.getSic())
                .sicDescription(response.getSicDescription())
                .insiderTransactionForOwnerExists(response.getInsiderTransactionForOwnerExists() == 1)
                .insiderTransactionForIssuerExists(response.getInsiderTransactionForIssuerExists() == 1)
                .tickers(response.getTickers())
                .exchanges(response.getExchanges())
                .ein(response.getEin())
                .description(response.getDescription())
                .website(response.getWebsite())
                .investorWebsite(response.getInvestorWebsite())
                .category(response.getCategory())
                .fiscalYearEnd(parseFiscalYearEnd(response.getFiscalYearEnd()))
                .stateOfIncorporation(response.getStateOfIncorporation())
                .stateOfIncorporationDescription(response.getStateOfIncorporationDescription())
                .build();
    }

    public List<Filling> toFillings(SecSubmissionResponse response) {
        List<Filling> fillings = new ArrayList<>();
        SecSubmissionResponse.Recent recent = response.getFilings().getRecent();

        if (recent == null || recent.getAccessionNumber() == null) {
            return fillings;
        }

        int count = recent.getAccessionNumber().size();
        for (int i = 0; i < count; i++) {
            try {
                Filling filling = Filling.builder()
                        .cik(response.getCik())
                        .company(response.getName())
                        .accessionNumber(getOrNull(recent.getAccessionNumber(), i))
                        .formType(FormType.builder()
                                .number(getOrNull(recent.getForm(), i))
                                .build())
                        .fillingDate(parseDate(getOrNull(recent.getFilingDate(), i)))
                        .reportDate(parseDate(getOrNull(recent.getReportDate(), i)))
                        .fileNumber(getOrNull(recent.getFileNumber(), i))
                        .filmNumber(getOrNull(recent.getFilmNumber(), i))
                        .items(getOrNull(recent.getItems(), i))
                        .primaryDocument(getOrNull(recent.getPrimaryDocument(), i))
                        .primaryDocDescription(getOrNull(recent.getPrimaryDocDescription(), i))
                        .isXBRL(getIntOrZero(recent.getIsXBRL(), i) == 1)
                        .isInlineXBRL(getIntOrZero(recent.getIsInlineXBRL(), i) == 1)
                        .build();
                fillings.add(filling);
            } catch (Exception e) {
                log.warn("Failed to parse filing at index {}: {}", i, e.getMessage());
            }
        }

        return fillings;
    }

    public List<Ticker> parseTickersJson(String json) {
        List<Ticker> tickers = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            root.fields().forEachRemaining(entry -> {
                JsonNode node = entry.getValue();
                JsonNode cikNode = node.get("cik_str");
                JsonNode tickerNode = node.get("ticker");
                JsonNode titleNode = node.get("title");

                if (cikNode == null || cikNode.isNull()
                        || tickerNode == null || tickerNode.isNull()
                        || titleNode == null || titleNode.isNull()) {
                    return;
                }

                String cikRaw = cikNode.asText();
                if (cikRaw == null || cikRaw.isBlank()) {
                    return;
                }

                int cikValue;
                try {
                    cikValue = Integer.parseInt(cikRaw);
                } catch (NumberFormatException e) {
                    log.debug("Skipping ticker with invalid CIK: {}", cikRaw);
                    return;
                }

                Ticker ticker = Ticker.builder()
                        .cik(String.format("%010d", cikValue))
                        .code(tickerNode.asText())
                        .name(titleNode.asText())
                        .build();
                tickers.add(ticker);
            });
        } catch (Exception e) {
            log.error("Failed to parse tickers JSON", e);
            throw new RuntimeException("Failed to parse SEC tickers response", e);
        }
        return tickers;
    }

    public List<Ticker> parseTickersExchangeJson(String json) {
        List<Ticker> tickers = new ArrayList<>();
        try {
            SecTickerExchangeResponse response = objectMapper.readValue(json, SecTickerExchangeResponse.class);

            for (List<Object> row : response.getData()) {
                if (row.size() >= 4 && row.get(0) != null && row.get(1) != null && row.get(2) != null && row.get(3) != null) {
                    int cikInt;
                    try {
                        cikInt = row.get(0) instanceof Integer ? (Integer) row.get(0) : Integer.parseInt(row.get(0).toString());
                    } catch (NumberFormatException e) {
                        log.debug("Skipping exchange ticker with invalid CIK: {}", row.get(0));
                        continue;
                    }
                    String cik = String.format("%010d", cikInt);
                    String name = row.get(1).toString();
                    String code = row.get(2).toString();
                    String exchangeCode = row.get(3).toString();

                    Ticker ticker = Ticker.builder()
                            .cik(cik)
                            .code(code)
                            .name(name)
                            .exchange(Exchange.builder().code(exchangeCode).build())
                            .build();
                    tickers.add(ticker);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse tickers exchange JSON", e);
            throw new RuntimeException("Failed to parse SEC tickers exchange response", e);
        }
        return tickers;
    }

    private Long parseFiscalYearEnd(String fiscalYearEnd) {
        if (fiscalYearEnd == null || fiscalYearEnd.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(fiscalYearEnd);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return DATE_FORMAT.parse(dateStr);
        } catch (ParseException e) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    private <T> T getOrNull(List<T> list, int index) {
        if (list == null || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    private int getIntOrZero(List<Integer> list, int index) {
        if (list == null || index >= list.size() || list.get(index) == null) {
            return 0;
        }
        return list.get(index);
    }
}

