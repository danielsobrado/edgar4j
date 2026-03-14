package org.jds.edgar4j.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.jds.edgar4j.integration.model.SecCompanyFactsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class SecResponseParserTest {

    private final SecResponseParser secResponseParser = new SecResponseParser(new ObjectMapper());

    @Test
    @DisplayName("parseCompanyFactsResponse should map nested taxonomy and unit entries")
    void parseCompanyFactsResponseShouldMapNestedFacts() {
        SecCompanyFactsResponse response = secResponseParser.parseCompanyFactsResponse("""
                {
                  "cik": "0000320193",
                  "entityName": "Apple Inc.",
                  "facts": {
                    "us-gaap": {
                      "CommonStockDividendsPerShareDeclared": {
                        "label": "Common stock dividends per share declared",
                        "units": {
                          "USD/shares": [
                            {
                              "end": "2025-09-27",
                              "val": 1.04,
                              "accn": "0000320193-25-000081",
                              "fy": 2025,
                              "fp": "FY",
                              "form": "10-K",
                              "filed": "2025-11-01"
                            }
                          ]
                        }
                      }
                    }
                  }
                }
                """);

        assertEquals("0000320193", response.getCik());
        assertEquals("Apple Inc.", response.getEntityName());
        assertNotNull(response.getFacts());
        assertNotNull(response.getFacts().get("us-gaap"));
        assertNotNull(response.getFacts().get("us-gaap").get("CommonStockDividendsPerShareDeclared"));
        assertEquals("Common stock dividends per share declared",
                response.getFacts().get("us-gaap").get("CommonStockDividendsPerShareDeclared").getLabel());
        assertEquals("2025-09-27",
                response.getFacts().get("us-gaap").get("CommonStockDividendsPerShareDeclared")
                        .getUnits().get("USD/shares").get(0).getEnd());
        assertEquals("10-K",
                response.getFacts().get("us-gaap").get("CommonStockDividendsPerShareDeclared")
                        .getUnits().get("USD/shares").get(0).getForm());
    }
}
