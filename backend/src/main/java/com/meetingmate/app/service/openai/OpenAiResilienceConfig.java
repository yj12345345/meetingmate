package com.meetingmate.app.service.openai;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Failure isolation for the OpenAI dependency, applied as
 * {@code CircuitBreaker( Retry( Bulkhead( call with timeout ) ) )}.
 *
 * <p>Order matters. The commonly documented order puts Retry outermost, so the breaker
 * counts every attempt. Tested against a flaky upstream (every other call 503) that
 * opened the breaker even though each retry succeeded, and 11 of 20 users got the
 * fallback. With the breaker outermost it judges the final outcome of a request.
 *
 * <ul>
 *   <li><b>Bulkhead</b> caps concurrent OpenAI calls (default 100 = half of Tomcat's 200
 *       threads) and rejects immediately when full, so AI traffic can never occupy the
 *       threads that login / my page need. Fault test: with timeouts alone, 12 AI req/s
 *       x 20s timeout = 240 blocked threads &gt; 200 and login queued for 7s.</li>
 *   <li><b>Circuit breaker</b> stops calling a failing OpenAI: after 50% failures in the
 *       last 20 calls it opens for 30s and requests get the fallback in milliseconds
 *       instead of each waiting 20s for a timeout. Then 3 trial calls decide to close.</li>
 *   <li><b>Retry</b> once, only for transient errors (429/502/503/504, connection-level
 *       errors). Never for timeouts: a timed-out call already cost 20s and money, a
 *       retry would double both.</li>
 * </ul>
 */
@Slf4j
@Configuration
public class OpenAiResilienceConfig {

    public static final String NAME = "openai";
    private static final Set<Integer> TRANSIENT_STATUS = Set.of(429, 502, 503, 504);

    @Bean
    public Bulkhead openAiBulkhead(@Value("${openai.max-concurrent-calls:100}") int maxConcurrentCalls,
                                   ObjectProvider<MeterRegistry> meterRegistry) {
        BulkheadRegistry registry = BulkheadRegistry.of(BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrentCalls)
                .maxWaitDuration(Duration.ZERO) // reject at once; waiting would hold a Tomcat thread
                .build());
        meterRegistry.ifAvailable(m -> TaggedBulkheadMetrics.ofBulkheadRegistry(registry).bindTo(m));
        return registry.bulkhead(NAME);
    }

    @Bean
    public CircuitBreaker openAiCircuitBreaker(ObjectProvider<MeterRegistry> meterRegistry) {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                // Normal p99 is ~8s; calls slower than 15s count as "slow" and 80% slow also opens.
                .slowCallDurationThreshold(Duration.ofSeconds(15))
                .slowCallRateThreshold(80)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                // Our own bulkhead rejecting a call says nothing about OpenAI's health.
                .ignoreExceptions(BulkheadFullException.class)
                .build());
        meterRegistry.ifAvailable(m -> TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(m));
        CircuitBreaker cb = registry.circuitBreaker(NAME);
        cb.getEventPublisher().onStateTransition(e -> log.warn("OpenAI circuit breaker {}", e.getStateTransition()));
        return cb;
    }

    @Bean
    public Retry openAiRetry(ObjectProvider<MeterRegistry> meterRegistry) {
        RetryRegistry registry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(2) // 1 original + 1 retry
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(Duration.ofMillis(500), 2.0, 0.5))
                .retryOnException(OpenAiResilienceConfig::isTransient)
                .build());
        meterRegistry.ifAvailable(m -> TaggedRetryMetrics.ofRetryRegistry(registry).bindTo(m));
        return registry.retry(NAME);
    }

    static boolean isTransient(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException
                    || t instanceof CallNotPermittedException
                    || t instanceof BulkheadFullException) {
                return false;
            }
            if (t instanceof WebClientResponseException e) {
                return TRANSIENT_STATUS.contains(e.getStatusCode().value());
            }
            if (t instanceof WebClientRequestException) {
                return true; // connection refused / reset / connect timeout
            }
        }
        return false;
    }
}
