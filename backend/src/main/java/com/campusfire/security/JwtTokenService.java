package com.campusfire.security;

import com.campusfire.auth.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {
    private final Key signingKey;
    private final long expirationSeconds;

    public JwtTokenService(@Value("${security.jwt.secret}") String secret,
                           @Value("${security.jwt.expiration-seconds}") long expirationSeconds) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public String create(UserAccount user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("userId", user.getId())
                .claim("role", user.getRoleCode())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder().setSigningKey(signingKey).build()
                .parseClaimsJws(token).getBody();
    }

    public long getExpirationSeconds() { return expirationSeconds; }
}

