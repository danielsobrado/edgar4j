package org.jds.edgar4j.config;

import org.jds.edgar4j.properties.CorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig {

    private final CorsProperties corsProperties;

    @Bean
    public CorsWebFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(corsProperties.isAllowCredentials());
        corsProperties.getAllowedOrigins().forEach(config::addAllowedOrigin);
        config.addAllowedHeader("*");
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("PATCH");
        config.addAllowedMethod("OPTIONS");
        config.addExposedHeader("Content-Disposition");
        config.setMaxAge(corsProperties.getMaxAgeSeconds());

        source.registerCorsConfiguration("/api/**", config);
        return new CorsWebFilter(source);
    }
}
