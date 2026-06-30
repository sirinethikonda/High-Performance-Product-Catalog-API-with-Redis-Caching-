package com.Product.middleware;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${RATE_LIMIT_MAX_REQUESTS:50}")
    private int maxRequests;

    @Value("${RATE_LIMIT_WINDOW_SECONDS:60}")
    private int windowSeconds;

    private static final String KEY_PREFIX = "rate_limit:";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty() || "unknown".equalsIgnoreCase(clientIp)) {
            clientIp = request.getRemoteAddr();
        } else {
            clientIp = clientIp.split(",")[0].trim();
        }

        String key = KEY_PREFIX + clientIp;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            
            if (count != null && count == 1) {
                redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
            }

            if (count != null && count > maxRequests) {
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                long retryAfter = (ttl != null && ttl > 0) ? ttl : windowSeconds;
                
                log.warn("Rate limit exceeded for client IP {}. Count: {}, limit: {}. Retry-after: {}s", 
                        clientIp, count, maxRequests, retryAfter);
                throw new RateLimitExceededException(retryAfter);
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis down during rate limit check. Falling back to fail-open.", e);
        }

        return true;
    }
}
