package com.library.config;

import com.library.user.Role;
import com.library.user.User;
import com.library.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the initial ADMIN account on first startup, from environment variables,
 * using the real {@link PasswordEncoder} bean. Deliberately not done via a SQL
 * migration: that would mean committing a bcrypt hash (and implicitly a fixed
 * password) to source control.
 */
@Component
public class AdminUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminEmail;
    private final String adminPassword;

    public AdminUserSeeder(UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            @Value("${ADMIN_USERNAME:admin}") String adminUsername,
                            @Value("${ADMIN_EMAIL:admin@library.local}") String adminEmail,
                            @Value("${ADMIN_PASSWORD:Admin123!}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByUsername(adminUsername)) {
            return;
        }
        User admin = new User(adminUsername, adminEmail, passwordEncoder.encode(adminPassword), Role.ADMIN);
        userRepository.save(admin);
        log.info("Seeded initial ADMIN user '{}'. Change ADMIN_PASSWORD in production.", adminUsername);
    }
}
