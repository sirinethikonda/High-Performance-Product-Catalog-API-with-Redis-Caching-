package com.Product.middleware;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Too Many Requests");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
