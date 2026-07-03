package com.library.user;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Authorization helper referenced from {@code @PreAuthorize} expressions to allow a
 * user to read/update their own profile without needing the ADMIN role.
 */
@Component("userSecurity")
public class UserSecurity {

    private final UserRepository userRepository;

    public UserSecurity(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isSelf(Long targetUserId, Authentication authentication) {
        if (authentication == null || targetUserId == null) {
            return false;
        }
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .map(User::getId)
                .map(targetUserId::equals)
                .orElse(false);
    }
}
