package com.expensetracker.config;

import com.expensetracker.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Slows down password guessing and account spam: at most N POSTs per time window to
 * /api/auth/login and /api/auth/register from one IP address. Over the limit -> 429 + Retry-After.
 * In-memory, so the counters reset on restart and are not shared between servers (fine for this project).
 * Behind a reverse proxy getRemoteAddr() is the proxy's address; set
 * server.forward-headers-strategy=native in the prod profile so the real client IP is used.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of("/api/auth/login", "/api/auth/register");
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final ObjectMapper objectMapper;
    private final int maxRequests;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimitFilter(ObjectMapper objectMapper, int maxRequests, Duration window) {
        this(objectMapper, maxRequests, window, System::currentTimeMillis);
    }

    /** The clock is injectable so tests do not have to wait a real minute. */
    RateLimitFilter(ObjectMapper objectMapper, int maxRequests, Duration window, LongSupplier clock) {
        this.objectMapper = objectMapper;
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = request.getRemoteAddr() + "|" + request.getRequestURI();
        long now = clock.getAsLong();
        long retryAfterSeconds = tryAcquire(key, now);
        if (retryAfterSeconds > 0) {
            reject(request, response, retryAfterSeconds);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Returns 0 if the request is allowed, otherwise the seconds until a slot frees up. */
    private long tryAcquire(String key, long now) {
        if (hits.size() > CLEANUP_THRESHOLD) {
            cleanup(now);
        }
        Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= windowMillis) {
                times.pollFirst();
            }
            if (times.size() >= maxRequests) {
                long millisLeft = times.peekFirst() + windowMillis - now;
                return Math.max(1, (millisLeft + 999) / 1000);
            }
            times.addLast(now);
            return 0;
        }
    }

    private void cleanup(long now) {
        hits.entrySet().removeIf(entry -> {
            Deque<Long> times = entry.getValue();
            synchronized (times) {
                return times.isEmpty() || now - times.peekLast() >= windowMillis;
            }
        });
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
                        long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError body = new ApiError(
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Too many attempts. Try again in " + retryAfterSeconds + " seconds.",
                request.getRequestURI(),
                null);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
