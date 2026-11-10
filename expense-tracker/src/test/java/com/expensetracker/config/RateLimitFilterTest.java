package com.expensetracker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitFilterTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        // 3 requests per minute, with a clock the test controls
        filter = new RateLimitFilter(new ObjectMapper().findAndRegisterModules(), 3,
                Duration.ofMinutes(1), now::get);
    }

    /** Sends one request and returns the response; chain.getRequest() is null if the filter blocked it. */
    private MockHttpServletResponse send(String method, String path, String ip, MockFilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void allowsRequestsUpToTheLimitThenReturns429() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse ok = send("POST", "/api/auth/login", "10.0.0.1", chain);
            assertEquals(200, ok.getStatus());
            assertNotNull(chain.getRequest());          // reached the controller
        }

        MockFilterChain blockedChain = new MockFilterChain();
        MockHttpServletResponse blocked = send("POST", "/api/auth/login", "10.0.0.1", blockedChain);

        assertEquals(429, blocked.getStatus());
        assertEquals("60", blocked.getHeader("Retry-After"));
        assertTrue(blocked.getContentAsString().contains("\"status\":429"));
        assertTrue(blocked.getContentAsString().contains("Too many attempts"));
        assertNull(blockedChain.getRequest());          // never reached the controller
    }

    @Test
    void allowsRequestsAgainAfterTheWindowPasses() throws Exception {
        for (int i = 0; i < 3; i++) {
            send("POST", "/api/auth/login", "10.0.0.1", new MockFilterChain());
        }
        assertEquals(429, send("POST", "/api/auth/login", "10.0.0.1", new MockFilterChain()).getStatus());

        now.addAndGet(Duration.ofMinutes(1).toMillis());   // one minute later

        MockFilterChain chain = new MockFilterChain();
        assertEquals(200, send("POST", "/api/auth/login", "10.0.0.1", chain).getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void eachIpAddressHasItsOwnLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            send("POST", "/api/auth/login", "10.0.0.1", new MockFilterChain());
        }
        assertEquals(429, send("POST", "/api/auth/login", "10.0.0.1", new MockFilterChain()).getStatus());

        MockFilterChain otherChain = new MockFilterChain();
        assertEquals(200, send("POST", "/api/auth/login", "10.0.0.2", otherChain).getStatus());
        assertNotNull(otherChain.getRequest());
    }

    @Test
    void loginAndRegisterAreCountedSeparately() throws Exception {
        for (int i = 0; i < 3; i++) {
            send("POST", "/api/auth/login", "10.0.0.1", new MockFilterChain());
        }
        MockFilterChain chain = new MockFilterChain();
        assertEquals(200, send("POST", "/api/auth/register", "10.0.0.1", chain).getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void otherUrlsAndMethodsAreNeverLimited() throws Exception {
        for (int i = 0; i < 20; i++) {
            MockFilterChain getChain = new MockFilterChain();
            send("GET", "/api/auth/login", "10.0.0.1", getChain);
            assertNotNull(getChain.getRequest());

            MockFilterChain txChain = new MockFilterChain();
            send("POST", "/api/transactions", "10.0.0.1", txChain);
            assertNotNull(txChain.getRequest());
        }
    }
}
