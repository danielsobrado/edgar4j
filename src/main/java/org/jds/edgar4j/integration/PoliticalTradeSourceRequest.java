package org.jds.edgar4j.integration;

public record PoliticalTradeSourceRequest(String assetType, int maxPages, int chunkPages, int pauseSeconds) {

    public PoliticalTradeSourceRequest(String assetType, int maxPages) {
        this(assetType, maxPages, 0, 0);
    }
}
