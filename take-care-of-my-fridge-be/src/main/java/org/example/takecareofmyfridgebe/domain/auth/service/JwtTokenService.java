package org.example.takecareofmyfridgebe.domain.auth.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class JwtTokenService {

    private static final String ISSUER = "take-care-of-my-fridge";
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;

    public JwtTokenService(@Value("${app.auth.jwt-secret}") String secret) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET은 32바이트 이상이어야 합니다.");
        }
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
    }

    public String issueAccess(Long memberId, Long sessionId, Instant now) {
        return issue(memberId, "access", now, now.plus(15, ChronoUnit.MINUTES),
                builder -> builder.claim("sid", sessionId.toString()));
    }

    public String issueRefresh(Long memberId, Instant now) {
        return issue(memberId, "refresh", now, now.plus(7, ChronoUnit.DAYS),
                builder -> builder.id(UUID.randomUUID().toString()));
    }

    public AccessClaims parseAccess(String token) {
        Jwt jwt = decode(token, "access");
        try {
            return new AccessClaims(Long.parseLong(jwt.getSubject()),
                    Long.parseLong(jwt.getClaimAsString("sid")));
        } catch (RuntimeException e) {
            throw new BadJwtException("Invalid access token claims", e);
        }
    }

    public Long parseRefresh(String token) {
        Jwt jwt = decode(token, "refresh");
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (RuntimeException e) {
            throw new BadJwtException("Invalid refresh token claims", e);
        }
    }

    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String issue(Long memberId, String type, Instant issuedAt, Instant expiresAt,
                         Consumer<JwtClaimsSet.Builder> extraClaims) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER).subject(memberId.toString())
                .issuedAt(issuedAt).expiresAt(expiresAt).claim("type", type);
        extraClaims.accept(claims);
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    private Jwt decode(String token, String expectedType) {
        Jwt jwt = decoder.decode(token);
        if (!expectedType.equals(jwt.getClaimAsString("type"))) {
            throw new BadJwtException("Wrong JWT type");
        }
        return jwt;
    }

    public record AccessClaims(Long memberId, Long sessionId) {
    }
}
