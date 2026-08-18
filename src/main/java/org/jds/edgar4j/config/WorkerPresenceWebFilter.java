package org.jds.edgar4j.config;

import static org.jds.edgar4j.constants.WorkerHttpConstants.BASE_PATH;
import static org.jds.edgar4j.constants.WorkerHttpConstants.SESSION_ID_HEADER;

import java.time.Clock;

import org.jds.edgar4j.service.WorkerPresenceRegistry;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
public class WorkerPresenceWebFilter implements WebFilter {

    private final WorkerPresenceRegistry presenceRegistry;
    private final Clock clock;

    public WorkerPresenceWebFilter(WorkerPresenceRegistry presenceRegistry, Clock clock) {
        this.presenceRegistry = presenceRegistry;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(BASE_PATH + "/")) {
            return chain.filter(exchange);
        }

        String sessionId = exchange.getRequest().getHeaders().getFirst(SESSION_ID_HEADER);
        if (sessionId == null || sessionId.isBlank()) {
            return chain.filter(exchange);
        }

        presenceRegistry.markSeen(sessionId, clock.instant());
        if (exchange.getRequest().getMethod() == HttpMethod.DELETE && (BASE_PATH + "/session").equals(path)) {
            return chain.filter(exchange).doOnSuccess(ignored -> presenceRegistry.remove(sessionId));
        }
        return chain.filter(exchange);
    }
}
