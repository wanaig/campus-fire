package com.campusfire.auth;

import com.campusfire.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private UserAccountRepository repository;
    private JwtTokenService tokenService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        repository = mock(UserAccountRepository.class);
        tokenService = mock(JwtTokenService.class);
        authService = new AuthService(repository, new BCryptPasswordEncoder(), tokenService);
    }

    @Test
    void shouldLoginWithCorrectPassword() {
        UserAccount user = new UserAccount(1L, "admin", "系统管理员",
                new BCryptPasswordEncoder().encode("Admin@123"), "ADMIN", true, false);
        when(repository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(tokenService.create(user)).thenReturn("signed-token");
        when(tokenService.getExpirationSeconds()).thenReturn(28800L);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("Admin@123");
        LoginResponse response = authService.login(request);

        assertEquals("signed-token", response.getAccessToken());
        assertEquals("ADMIN", response.getUser().getRoleCode());
    }

    @Test
    void shouldRejectWrongPassword() {
        UserAccount user = new UserAccount(1L, "admin", "系统管理员",
                new BCryptPasswordEncoder().encode("Admin@123"), "ADMIN", true, false);
        when(repository.findByUsername("admin")).thenReturn(Optional.of(user));
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrong-password");

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }
}

