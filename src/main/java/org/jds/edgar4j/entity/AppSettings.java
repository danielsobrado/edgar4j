package org.jds.edgar4j.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = false)
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Document(collection = "app_settings")
public class AppSettings {

    @Id
    private String id;

    @Builder.Default
    private String userAgent = "Edgar4j/1.0 (contact@example.com)";

    @Builder.Default
    private boolean autoRefresh = true;

    @Builder.Default
    private int refreshInterval = 300;

    @Builder.Default
    private boolean darkMode = false;

    @Builder.Default
    private boolean emailNotifications = false;
}
