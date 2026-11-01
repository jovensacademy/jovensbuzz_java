package com.jovensbuzz;

import com.jovensbuzz.admin.entity.AdminUser;
import com.jovensbuzz.common.validation.InputSanitizer;
import com.jovensbuzz.common.validation.PasswordPolicy;
import com.jovensbuzz.common.util.ReferenceGenerator;
import com.jovensbuzz.security.ClientIpResolver;
import com.jovensbuzz.security.JwtTokenProvider;
import com.jovensbuzz.security.RateLimitFilter;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilitiesTest {

    @Test
    void sanitizer_removesMarkupAndScriptSchemes() {
        assertEquals("Hello world", InputSanitizer.plainText("  <b>Hello</b>\n   world "));
        assertEquals("click alert(1)", InputSanitizer.plainText("<a href=x>click</a> javascript:alert(1)"));
        assertEquals("line one\n\nline two", InputSanitizer.multiLineText("line one\r\n\r\n\r\n\r\nline two"));
        assertNull(InputSanitizer.plainText("   <br/>  "));
    }

    @Test
    void sanitizer_neutralisesSpreadsheetFormulas() {
        assertEquals("'=HYPERLINK(\"x\")", InputSanitizer.spreadsheetSafe("=HYPERLINK(\"x\")"));
        assertEquals("'+cmd", InputSanitizer.spreadsheetSafe("+cmd"));
        assertEquals("Plain", InputSanitizer.spreadsheetSafe("Plain"));
    }

    @Test
    void sanitizer_acceptsOnlyHttpUrls() {
        assertEquals("https://instagram.com/jovensbuzz", InputSanitizer.safeUrl(" https://instagram.com/jovensbuzz "));
        assertNull(InputSanitizer.safeUrl("javascript:alert(1)"));
        assertNull(InputSanitizer.safeUrl("https://x.com/\"onmouseover=alert(1)"));
        assertEquals("", InputSanitizer.safeUrl(""));
    }

    @Test
    void passwordPolicy_requiresStrongPasswords() {
        assertTrue(PasswordPolicy.validate("Jovensbuzz@2026", "jovens_buzz_2026").isEmpty());
        assertFalse(PasswordPolicy.validate("short1!A", "admin").isEmpty());
        assertFalse(PasswordPolicy.validate("alllowercase123!", "admin").isEmpty());
        assertFalse(PasswordPolicy.validate("NoSymbolsHere123", "admin").isEmpty());
        assertFalse(PasswordPolicy.validate("Admin-Password-2026", "admin").isEmpty(), "must not contain username");
    }

    @Test
    void referenceGenerator_isUnpredictableAndWellFormed() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String ref = ReferenceGenerator.generateEnquiryReference();
            assertTrue(ref.matches("^JB-\\d{4}-[2-9A-HJ-NP-Z]{6}$"), ref);
            seen.add(ref);
        }
        assertTrue(seen.size() > 1990, "references should practically never collide");
    }

    @Test
    void jwt_roundTripCarriesTokenVersion_andRejectsTampering() {
        JwtTokenProvider provider = new JwtTokenProvider(JwtTokenProvider.DEFAULT_DEV_SECRET, 60_000);
        AdminUser admin = new AdminUser("jovens_buzz_2026", "hash", "Admin", "a@example.com", "ROLE_ADMIN");
        admin.setTokenVersion(3);

        String token = provider.generateToken(admin).token();
        Claims claims = provider.parseClaims(token);
        assertNotNull(claims);
        assertEquals("jovens_buzz_2026", claims.getSubject());
        assertEquals(3, JwtTokenProvider.tokenVersionOf(claims));

        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("aaa") ? "bbb" : "aaa");
        assertNull(provider.parseClaims(tampered));

        JwtTokenProvider otherKey = new JwtTokenProvider("c2VjcmV0LWtleS1mb3ItYW5vdGhlci1lbnZpcm9ubWVudC0xMjM0NTY3OA==", 60_000);
        assertNull(otherKey.parseClaims(token));
    }

    @Test
    void jwt_expiredTokenIsRejected() {
        JwtTokenProvider provider = new JwtTokenProvider(JwtTokenProvider.DEFAULT_DEV_SECRET, -1_000);
        AdminUser admin = new AdminUser("u", "hash", "Admin", "a@example.com", "ROLE_ADMIN");
        assertNull(provider.parseClaims(provider.generateToken(admin).token()));
    }

    @Test
    void rateLimiter_blocksAfterLimitPerIp() throws Exception {
        RateLimitFilter filter = new RateLimitFilter("127.0.0.1", 3, 600, 10, 300);

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse ok = send(filter, "/api/v1/enquiries", "203.0.113.5");
            assertEquals(200, ok.getStatus());
        }
        MockHttpServletResponse blocked = send(filter, "/api/v1/enquiries", "203.0.113.5");
        assertEquals(429, blocked.getStatus());
        assertNotNull(blocked.getHeader("Retry-After"));

        // A different client is unaffected, and other endpoints are not limited
        assertEquals(200, send(filter, "/api/v1/enquiries", "203.0.113.9").getStatus());
        assertEquals(200, send(filter, "/api/v1/services", "203.0.113.5").getStatus());
    }

    @Test
    void rateLimiter_ignoresSpoofedForwardedForFromUntrustedPeer() throws Exception {
        RateLimitFilter filter = new RateLimitFilter("127.0.0.1", 1, 600, 10, 300);
        MockHttpServletRequest first = request("/api/v1/enquiries", "198.51.100.7");
        first.addHeader("X-Forwarded-For", "1.1.1.1");
        MockHttpServletResponse r1 = new MockHttpServletResponse();
        filter.doFilter(first, r1, new MockFilterChain());

        MockHttpServletRequest second = request("/api/v1/enquiries", "198.51.100.7");
        second.addHeader("X-Forwarded-For", "2.2.2.2");
        MockHttpServletResponse r2 = new MockHttpServletResponse();
        filter.doFilter(second, r2, new MockFilterChain());

        assertEquals(429, r2.getStatus(), "rotating X-Forwarded-For must not bypass the limit");
    }

    @Test
    void rateLimiter_behindTrustedProxy_usesRightmostUntrustedAddress() throws Exception {
        RateLimitFilter filter = new RateLimitFilter("127.0.0.1", 1, 600, 10, 300);
        // The proxy appends the real client (203.0.113.50); the left-hand entries are client-supplied
        MockHttpServletRequest first = request("/api/v1/enquiries", "127.0.0.1");
        first.addHeader("X-Forwarded-For", "1.1.1.1, 203.0.113.50");
        MockHttpServletResponse r1 = new MockHttpServletResponse();
        filter.doFilter(first, r1, new MockFilterChain());
        assertEquals(200, r1.getStatus());

        MockHttpServletRequest second = request("/api/v1/enquiries", "127.0.0.1");
        second.addHeader("X-Forwarded-For", "2.2.2.2, 203.0.113.50, 127.0.0.1");
        MockHttpServletResponse r2 = new MockHttpServletResponse();
        filter.doFilter(second, r2, new MockFilterChain());
        assertEquals(429, r2.getStatus(), "a spoofed left-hand X-Forwarded-For entry must not bypass the limit");

        assertEquals("203.0.113.9", new ClientIpResolver("127.0.0.1").resolve(withForwarded("203.0.113.9")));
    }

    private static MockHttpServletRequest withForwarded(String forwarded) {
        MockHttpServletRequest request = request("/api/v1/enquiries", "127.0.0.1");
        request.addHeader("X-Forwarded-For", forwarded);
        return request;
    }

    private static MockHttpServletRequest request(String uri, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr(ip);
        return request;
    }

    private static MockHttpServletResponse send(RateLimitFilter filter, String uri, String ip) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(uri, ip), response, new MockFilterChain());
        return response;
    }
}
