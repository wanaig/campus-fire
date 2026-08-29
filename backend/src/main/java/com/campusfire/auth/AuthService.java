package com.campusfire.auth;

import com.campusfire.security.JwtTokenService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;

    public AuthService(UserAccountRepository repository, PasswordEncoder passwordEncoder,
                       JwtTokenService tokenService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public LoginResponse login(LoginRequest request) {
        UserAccount user = repository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("账号或密码错误"));
        if (!user.isEnabled()) {
            throw new DisabledException("账号已停用，请联系管理员");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("账号或密码错误");
        }
        AuthenticatedUser current = toAuthenticatedUser(user);
        return new LoginResponse(tokenService.create(user), tokenService.getExpirationSeconds(), current);
    }

    public AuthenticatedUser loadCurrentUser(String username) {
        return repository.findByUsername(username).map(this::toAuthenticatedUser)
                .orElseThrow(() -> new BadCredentialsException("登录状态无效"));
    }

    private AuthenticatedUser toAuthenticatedUser(UserAccount user) {
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getRoleCode(), user.isAccessibilityMode());
    }
}

