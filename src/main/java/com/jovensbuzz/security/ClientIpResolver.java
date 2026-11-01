package com.jovensbuzz.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the real client IP. X-Forwarded-For is honoured only when the direct peer is a
 * configured trusted proxy (e.g. the Next.js server or a load balancer); otherwise a client
 * could spoof the header to dodge rate limits. Proxies append to the header, so only the
 * right-hand entries are trustworthy: the client is the rightmost address that is not itself
 * a trusted proxy (anything left of it may have been sent by the client).
 */
public class ClientIpResolver {

    private final Set<String> trustedProxies;

    public ClientIpResolver(String trustedProxies) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote != null && trustedProxies.contains(remote)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                String[] hops = forwarded.split(",");
                for (int i = hops.length - 1; i >= 0; i--) {
                    String hop = hops[i].trim();
                    if (!hop.isEmpty() && !trustedProxies.contains(hop)) {
                        return hop;
                    }
                }
            }
        }
        return remote != null ? remote : "unknown";
    }
}
