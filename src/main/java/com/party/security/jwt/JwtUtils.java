package com.party.security.jwt;

import com.party.security.services.UserDetailsImpl;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.security.core.userdetails.UserDetails;
import javax.crypto.SecretKey;
import java.util.Date;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;

@Component
public class JwtUtils {
    private final SecretKey key;
    private final long expiration;

    public JwtUtils(@Value("${spring.app.secret-key}") String secret,
                    @Value("${spring.app.expiration-in-milliseconds}") long expiration) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        if (expiration <= 0) throw new IllegalArgumentException("JWT expiration must be positive");
        this.expiration = expiration;
    }

    public String generateJwtTokenFromUsername(UserDetailsImpl user) {
        long now = System.currentTimeMillis();
        return Jwts.builder().subject(user.getId().toString()).claim("credentials", credentialsStamp(user)).issuedAt(new Date(now))
                .expiration(new Date(Math.addExact(now, expiration))).signWith(key).compact();
    }

    public Long getUserIdFromJwtToken(String token) {
        String subject = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
        long id = Long.parseLong(subject);
        if (id <= 0) throw new IllegalArgumentException("Invalid subject");
        return id;
    }

    public boolean matchesCurrentCredentials(String token, UserDetails user) {
        String stamp = Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
                .getPayload().get("credentials", String.class);
        return stamp != null && MessageDigest.isEqual(stamp.getBytes(StandardCharsets.UTF_8),
                credentialsStamp(user).getBytes(StandardCharsets.UTF_8));
    }

    private String credentialsStamp(UserDetails user) {
        try {
            // A keyed digest binds tokens to the stored hash without exposing it in JWT claims.
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(
                    ("legalcell.credentials.v1:" + user.getPassword()).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot bind token to credentials", exception);
        }
    }
}
