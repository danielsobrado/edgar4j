package org.jds.edgar4j.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UsaSpendingCsvPageResponse {

    private String jobId;
    private String fileName;
    private List<String> headers;
    private List<List<String>> rows;
    private List<List<UsaSpendingCompanyMatchResponse>> rowMatches;
    private int page;
    private int size;
    private long totalRows;
    private int totalPages;
}
