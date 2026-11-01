package com.jovensbuzz.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window, per-IP rate limiting for abuse-prone public endpoints
 * (enquiry submission and admin login). In-memory, so limits are per instance;
 * put a shared limiter (gateway / Redis) in front when running several instances.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final int MAX_TRACKED_KEYS = 50_000;

    private final ClientIpResolver clientIpResolver;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final int enquiryLimit;
    private final long enquiryWindowMs;
    private final int loginLimit;
    private final long loginWindowMs;

    public RateLimitFilter(
            @Value("${app.security.trusted-proxies:127.0.0.1,0:0:0:0:0:0:0:1,::1}") String trustedProxies,
            @Value("${app.rate-limit.enquiry.max-requests:5}") int enquiryLimit,
            @Value("${app.rate-limit.enquiry.window-seconds:600}") long enquiryWindowSeconds,
            @Value("${app.rate-limit.login.max-requests:10}") int loginLimit,
            @Value("${app.rate-limit.login.window-seconds:300}") long loginWindowSeconds) {
        this.clientIpResolver = new ClientIpResolver(trustedProxies);
        this.enquiryLimit = enquiryLimit;
        this.enquiryWindowMs = enquiryWindowSeconds * 1000;
        this.loginLimit = loginLimit;
        this.loginWindowMs = loginWindowSeconds * 1000;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || bucketFor(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String bucket = bucketFor(request.getRequestURI());
        boolean isLogin = "login".equals(bucket);
        int limit = isLogin ? loginLimit : enquiryLimit;
        long window = isLogin ? loginWindowMs : enquiryWindowMs;
        String key = bucket + ":" + clientIpResolver.resolve(request);

        long retryAfterSeconds = tryAcquire(key, limit, window);
        if (retryAfterSeconds > 0) {
            log.warn("Rate limit exceeded for {} (bucket={})", key, bucket);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Too many requests. Please wait a few minutes and try again.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Records a hit and returns 0 when allowed, otherwise the seconds until a slot frees up.
     */
    long tryAcquire(String key, int limit, long windowMs) {
        long now = System.currentTimeMillis();
        if (hits.size() > MAX_TRACKED_KEYS) {
            hits.clear(); // crude memory guard under a flood of distinct IPs
        }
        Deque<Long> timestamps = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowMs) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                return Math.max(1, (windowMs - (now - timestamps.peekFirst())) / 1000);
            }
            timestamps.addLast(now);
            return 0;
        }
    }

    private static String bucketFor(String uri) {
        if (uri == null) return null;
        if (uri.equals("/api/v1/enquiries") || uri.equals("/api/v1/enquiries/")) return "enquiry";
        if (uri.equals("/api/v1/auth/login")) return "login";
        return null;
    }
}
