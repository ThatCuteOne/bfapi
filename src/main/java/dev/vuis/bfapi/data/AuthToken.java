package dev.vuis.bfapi.data;

import java.time.Instant;

import org.jetbrains.annotations.NotNull;

public class AuthToken {
    private final String token;
    private final Instant expiresAt;

    public AuthToken(@NotNull String token, Instant expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
    }
    public AuthToken(@NotNull String token) {
            this.token = token;
            this.expiresAt = null;
    }
    public String getToken() {
        return token;
    }
    public Instant getExpiryTime() {
        return expiresAt;
    }
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

}
