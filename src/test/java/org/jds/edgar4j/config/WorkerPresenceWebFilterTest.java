package org.jds.edgar4j.config;

import static org.jds.edgar4j.constants.WorkerHttpConstants.SESSION_ID_HEADER;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.jds.edgar4j.service.WorkerPresenceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

@ExtendWith(MockitoExtension.class)
class WorkerPresenceWebFilterTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");

    @Mock
    private WorkerPresenceRegistry presenceRegistry;

    @Test
    void successfulWorkerRequestMarksPresence() {
        WorkerPresenceWebFilter filter = new WorkerPresenceWebFilter(
                presenceRegistry,
                Clock.fixed(NOW, ZoneOffset.UTC));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/workers/tasks/lease")
                        .header(SESSION_ID_HEADER, "session-1"));

        filter.filter(exchange, current -> {
                    current.getResponse().setStatusCode(HttpStatus.OK);
                    return current.getResponse().setComplete();
                })
                .block();

        verify(presenceRegistry).markSeen("session-1", NOW);
    }

    @Test
    void rejectedWorkerRequestDoesNotMarkPresence() {
        WorkerPresenceWebFilter filter = new WorkerPresenceWebFilter(
                presenceRegistry,
                Clock.fixed(NOW, ZoneOffset.UTC));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/workers/tasks/lease")
                        .header(SESSION_ID_HEADER, "spoofed-session"));

        filter.filter(exchange, current -> {
                    current.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return current.getResponse().setComplete();
                })
                .block();

        verify(presenceRegistry, never()).markSeen("spoofed-session", NOW);
    }
}
