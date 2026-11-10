package com.meetingmate.app.service.openai;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenAiResilienceConfigTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<io.micrometer.core.instrument.MeterRegistry> noMetrics = mock(ObjectProvider.class);
    private final OpenAiResilienceConfig config = new OpenAiResilienceConfig();

    @Test
    void retriesOnlyTransientErrors() {
        assertThat(OpenAiResilienceConfig.isTransient(status(503))).isTrue();
        assertThat(OpenAiResilienceConfig.isTransient(status(429))).isTrue();
        assertThat(OpenAiResilienceConfig.isTransient(new WebClientRequestException(
                new IOException("Connection reset by peer"), HttpMethod.POST, URI.create("http://x"), new HttpHeaders())))
                .isTrue();

        assertThat(OpenAiResilienceConfig.isTransient(status(400))).isFalse();
        assertThat(OpenAiResilienceConfig.isTransient(status(401))).isFalse();
        // A timed-out call is never retried: it already cost the full timeout (and money).
        assertThat(OpenAiResilienceConfig.isTransient(new RuntimeException(new TimeoutException()))).isFalse();
    }

    @Test
    void breakerOpensAfterFailuresAndThenRejectsWithoutCalling() {
        CircuitBreaker cb = config.openAiCircuitBreaker(noMetrics);
        for (int i = 0; i < 10; i++) {
            cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException(new TimeoutException()));
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.tryAcquirePermission()).isFalse();
        assertThat(CallNotPermittedException.createCallNotPermittedException(cb)).isNotNull();
    }

    @Test
    void bulkheadRejectsImmediatelyWhenFull() {
        Bulkhead bulkhead = config.openAiBulkhead(1, noMetrics);
        assertThat(bulkhead.tryAcquirePermission()).isTrue();
        assertThat(bulkhead.tryAcquirePermission()).isFalse();
        bulkhead.onComplete();
        assertThat(bulkhead.tryAcquirePermission()).isTrue();
    }

    @Test
    void bulkheadRejectionDoesNotCountAsOpenAiFailure() {
        CircuitBreaker cb = config.openAiCircuitBreaker(noMetrics);
        for (int i = 0; i < 20; i++) {
            cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS,
                    BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("x")));
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private static WebClientResponseException status(int code) {
        return WebClientResponseException.create(code, "status " + code, new HttpHeaders(), new byte[0], null);
    }
}
