package com.library.security;

public record AuthResponse(String accessToken, String tokenType, String username, String role) {
    public static AuthResponse of(String token, String username, String role) {
        return new AuthResponse(token, "Bearer", username, role);
    }
}
