package com.library.security;

import com.library.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.getJwt().setSecret("unit-test-secret-key-must-be-long-enough-0123456789");
        appProperties.getJwt().setExpirationMs(60_000);
        jwtService = new JwtService(appProperties);
    }

    @Test
    void generateToken_producesTokenThatIsValidForTheSameUsername() {
        String token = jwtService.generateToken("alice", "USER");

        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtService.extractRole(token)).isEqualTo("USER");
        assertThat(jwtService.isTokenValid(token, "alice")).isTrue();
    }

    @Test
    void isTokenValid_returnsFalse_whenUsernameDoesNotMatch() {
        String token = jwtService.generateToken("alice", "USER");

        assertThat(jwtService.isTokenValid(token, "bob")).isFalse();
    }

    @Test
    void isTokenValid_returnsFalse_forGarbageToken() {
        assertThat(jwtService.isTokenValid("not-a-real-jwt", "alice")).isFalse();
    }

    @Test
    void isTokenValid_returnsFalse_whenTokenIsExpired() throws InterruptedException {
        AppProperties expiringProps = new AppProperties();
        expiringProps.getJwt().setSecret("unit-test-secret-key-must-be-long-enough-0123456789");
        expiringProps.getJwt().setExpirationMs(1); // 1ms
        JwtService shortLivedJwtService = new JwtService(expiringProps);

        String token = shortLivedJwtService.generateToken("alice", "USER");
        Thread.sleep(15);

        assertThat(shortLivedJwtService.isTokenValid(token, "alice")).isFalse();
    }
}
