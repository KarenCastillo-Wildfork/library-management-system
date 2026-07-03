package com.library.user;

import java.time.Instant;

/** Public representation of a user; never exposes the password hash. */
public record UserResponse(
        Long id,
        String username,
        String email,
        Role role,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
