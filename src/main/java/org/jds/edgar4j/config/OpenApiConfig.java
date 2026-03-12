package org.jds.edgar4j.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger documentation configuration.
 *
 * Access the API documentation at:
 * - Swagger UI: /swagger-ui.html
 * - OpenAPI JSON: /v3/api-docs
 * - OpenAPI YAML: /v3/api-docs.yaml
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Edgar4j API")
                        .version("1.0.0")
                        .description("Edgar4j is a Java-based application for fetching, parsing, and analyzing SEC EDGAR filings. This API provides access to company information, SEC filings, and parsed form data including Form 4, Form 13F, Form 13D/G, Form 8-K, Form 3, Form 5, Form 6-K, and Form 20-F. Features: Company search and management, SEC filing retrieval and parsing, Insider trading data, Institutional holdings, Beneficial ownership, Current reports, Ownership forms, Foreign issuer reports, Download job management, Dashboard statistics")
                        .contact(new Contact()
                                .name("Edgar4j")
                                .url("https://github.com/edgar4j/edgar4j"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local development server")));
    }
}
