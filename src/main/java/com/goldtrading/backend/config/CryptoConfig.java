package com.goldtrading.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

@Configuration
public class CryptoConfig {
    private static final int MIN_JWT_KEY_BYTES = 32;
    private static final Set<String> LOCAL_PROFILES = Set.of("local", "test");
    private final Environment environment;

    public CryptoConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String secret) {
        byte[] decoded = decodeOrGenerateForLocal(secret);
        if (decoded.length < MIN_JWT_KEY_BYTES) {
            throw new IllegalStateException("JWT secret is too weak; provide at least 256-bit base64 key via APP_JWT_SECRET_BASE64");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    private byte[] decodeOrGenerateForLocal(String secret) {
        String trimmed = secret == null ? "" : secret.trim();
        if (trimmed.isEmpty()) {
            if (isLocalProfile()) {
                byte[] generated = new byte[MIN_JWT_KEY_BYTES];
                new SecureRandom().nextBytes(generated);
                return generated;
            }
            throw new IllegalStateException("Missing APP_JWT_SECRET_BASE64 in non-local environment");
        }

        try {
            return Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid APP_JWT_SECRET_BASE64 format; expected base64-encoded key", ex);
        }
    }

    private boolean isLocalProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if (LOCAL_PROFILES.contains(profile.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}

