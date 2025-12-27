package org.jds.edgar4j.config;

import org.jds.edgar4j.service.SettingsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
import reactor.netty.http.client.HttpClient;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final SettingsService settingsService;

    @Bean
    @ConditionalOnMissingBean
    public WebClient.Builder webClientBuilder() {
        ExchangeFilterFunction userAgentFilter = (request, next) -> {
            ClientRequest.Builder builder = ClientRequest.from(request);
            String userAgent = request.headers().getFirst(HttpHeaders.USER_AGENT);
            if (userAgent == null || userAgent.isBlank()) {
                builder.header(HttpHeaders.USER_AGENT, settingsService.getUserAgent());
            }
            String accept = request.headers().getFirst(HttpHeaders.ACCEPT);
            if (accept == null || accept.isBlank()) {
                builder.header(HttpHeaders.ACCEPT, "application/json, text/html, application/xml");
            }
            return next.exchange(builder.build());
        };

        HttpClient httpClient = HttpClient.create()
                .followRedirect(true);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(userAgentFilter);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebClient webClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.build();
    }
}
