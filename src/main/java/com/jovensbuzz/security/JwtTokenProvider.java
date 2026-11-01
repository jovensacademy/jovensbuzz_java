package com.jovensbuzz.security;

import com.jovensbuzz.admin.entity.AdminUser;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies short-lived admin access tokens.
 * Each token carries the admin's token version ("tv"); bumping the version in the
 * database (logout, password change) revokes every token issued before it.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    public static final String DEFAULT_DEV_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String TOKEN_VERSION_CLAIM = "tv";
    private static final String ISSUER = "jovens-buzz-api";

    private final SecretKey key;
    private final long jwtExpirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret:" + DEFAULT_DEV_SECRET + "}") String secret,
            @Value("${app.jwt.expiration-ms:3600000}") long jwtExpirationMs) {
        byte[] keyBytes = Decoders.BASE64.decode(secret.length() % 4 != 0 ? padBase64(secret) : secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.jwtExpirationMs = jwtExpirationMs;
    }

    private static String padBase64(String input) {
        int paddingLength = (4 - (input.length() % 4)) % 4;
        return input + "=".repeat(paddingLength);
    }

    public IssuedToken generateToken(AdminUser admin) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(jwtExpirationMs);

        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(admin.getUsername())
                .claim("roles", admin.getRole())
                .claim(TOKEN_VERSION_CLAIM, admin.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /**
     * Returns verified claims, or null when the token is malformed, expired or forged.
     */
    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return null;
        }
    }

    public static int tokenVersionOf(Claims claims) {
        Integer version = claims.get(TOKEN_VERSION_CLAIM, Integer.class);
        return version != null ? version : -1;
    }

    public record IssuedToken(String token, Instant expiresAt) {}
}
