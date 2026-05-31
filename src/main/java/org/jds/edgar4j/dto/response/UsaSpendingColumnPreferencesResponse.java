package org.jds.edgar4j.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsaSpendingColumnPreferencesResponse {
    private List<String> hiddenColumns;
}
