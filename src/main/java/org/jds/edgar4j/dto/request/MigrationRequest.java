package org.jds.edgar4j.dto.request;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class MigrationRequest {

    private String path;
    private List<String> collections = new ArrayList<>();
}
