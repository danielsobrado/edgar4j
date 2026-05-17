package org.jds.edgar4j.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.jds.edgar4j.integration.model.SecCompanyConceptResponse;
import org.jds.edgar4j.integration.model.SecCompanyFactsResponse;
import org.jds.edgar4j.integration.model.SecFrameResponse;
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

    @Test
    @DisplayName("parseCompanyConceptResponse should map concept metadata and unit entries")
    void parseCompanyConceptResponseShouldMapConceptFacts() {
        SecCompanyConceptResponse response = secResponseParser.parseCompanyConceptResponse("""
                {
                  "cik": "0000320193",
                  "taxonomy": "us-gaap",
                  "tag": "CommonStockDividendsPerShareDeclared",
                  "label": "Common stock dividends per share declared",
                  "description": "Cash dividends declared per common share.",
                  "entityName": "Apple Inc.",
                  "units": {
                    "USD/shares": [
                      {
                        "end": "2025-09-27",
                        "val": 1.04,
                        "accn": "0000320193-25-000081",
                        "fy": 2025,
                        "fp": "FY",
                        "form": "10-K",
                        "filed": "2025-11-01",
                        "frame": "CY2025"
                      }
                    ]
                  }
                }
                """);

        assertEquals("0000320193", response.getCik());
        assertEquals("us-gaap", response.getTaxonomy());
        assertEquals("CommonStockDividendsPerShareDeclared", response.getTag());
        assertEquals("Apple Inc.", response.getEntityName());
        assertEquals("2025-09-27", response.getUnits().get("USD/shares").get(0).getEnd());
        assertEquals("CY2025", response.getUnits().get("USD/shares").get(0).getFrame());
    }

    @Test
    @DisplayName("parseFrameResponse should map cross-company frame entries")
    void parseFrameResponseShouldMapFrameData() {
        SecFrameResponse response = secResponseParser.parseFrameResponse("""
                {
                  "taxonomy": "us-gaap",
                  "tag": "CommonStockDividendsPerShareDeclared",
                  "ccp": "CY2023Q4I",
                  "uom": "USD/shares",
                  "label": "Common stock dividends per share declared",
                  "description": "Cash dividends declared per common share.",
                  "data": [
                    {
                      "accn": "0000320193-23-000106",
                      "cik": 320193,
                      "entityName": "Apple Inc.",
                      "loc": "US-CA",
                      "end": "2023-09-30",
                      "val": 0.24
                    }
                  ]
                }
                """);

        assertEquals("us-gaap", response.getTaxonomy());
        assertEquals("CommonStockDividendsPerShareDeclared", response.getTag());
        assertEquals("CY2023Q4I", response.getCcp());
        assertEquals("USD/shares", response.getUom());
        assertEquals(1, response.getData().size());
        assertEquals(320193, response.getData().get(0).getCik());
        assertEquals("Apple Inc.", response.getData().get(0).getEntityName());
    }
}
