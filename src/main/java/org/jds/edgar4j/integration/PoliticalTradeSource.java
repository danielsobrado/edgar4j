package org.jds.edgar4j.integration;

import java.util.List;

import org.jds.edgar4j.model.PoliticalTrade;

public interface PoliticalTradeSource {

    String sourceName();

    List<PoliticalTrade> fetch(PoliticalTradeSourceRequest request);
}
