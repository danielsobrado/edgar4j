package org.jds.edgar4j.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class WebConfig implements WebFluxConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:3000",
                        "http://localhost:5173",
                        "http://localhost:5174",
                        "http://127.0.0.1:3000",
                        "http://127.0.0.1:5173",
                        "http://127.0.0.1:5174"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Bean
    public CorsWebFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(true);
        config.addAllowedOrigin("http://localhost:3000");
        config.addAllowedOrigin("http://localhost:5173");
        config.addAllowedOrigin("http://localhost:5174");
        config.addAllowedOrigin("http://127.0.0.1:3000");
        config.addAllowedOrigin("http://127.0.0.1:5173");
        config.addAllowedOrigin("http://127.0.0.1:5174");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.addExposedHeader("Content-Disposition");

        source.registerCorsConfiguration("/api/**", config);
        return new CorsWebFilter(source);
    }
}
