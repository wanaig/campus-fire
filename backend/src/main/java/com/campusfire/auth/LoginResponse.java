package com.campusfire.auth;

public class LoginResponse {
    private final String accessToken;
    private final String tokenType;
    private final long expiresIn;
    private final AuthenticatedUser user;

    public LoginResponse(String accessToken, long expiresIn, AuthenticatedUser user) {
        this.accessToken = accessToken;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getAccessToken() { return accessToken; }
    public String getTokenType() { return tokenType; }
    public long getExpiresIn() { return expiresIn; }
    public AuthenticatedUser getUser() { return user; }
}

