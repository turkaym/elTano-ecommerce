package com.eltano.ecommerce.catalog.jobs.worker;

import java.time.Instant;

import org.springframework.stereotype.Component;

@Component
public class AdminCatalogJobRetryPolicy {

    public boolean isRetryable(Throwable error) {
        return error instanceof RetryableJobException;
    }

    public boolean shouldRetry(int attemptCount, int maxAttempts, Throwable error) {
        return attemptCount < maxAttempts && isRetryable(error);
    }

    public Instant nextAttemptAt(Instant now, int attemptCount) {
        long delaySeconds = Math.min(60L, Math.max(5L, attemptCount * 5L));
        return now.plusSeconds(delaySeconds);
    }

    public static class RetryableJobException extends RuntimeException {
        public RetryableJobException(String message) {
            super(message);
        }
    }
}
