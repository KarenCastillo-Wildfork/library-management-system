package com.library.user;

import jakarta.validation.constraints.Email;

/**
 * Fields a caller may update on a user. {@code role} is only honoured when the
 * caller is an ADMIN (enforced in {@link UserService}); a regular user updating
 * their own profile cannot escalate their own role.
 */
public record UserUpdateRequest(
        @Email(message = "email must be a valid address") String email,
        Role role
) {
}
