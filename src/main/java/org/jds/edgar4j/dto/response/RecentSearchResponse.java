package org.jds.edgar4j.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentSearchResponse {

    private String id;
    private String query;
    private String type;
    private LocalDateTime timestamp;
    private int resultCount;
}
