package com.campusfire.auth;

public class AuthenticatedUser {
    private final Long id;
    private final String username;
    private final String displayName;
    private final String roleCode;
    private final boolean accessibilityMode;

    public AuthenticatedUser(Long id, String username, String displayName, String roleCode,
                             boolean accessibilityMode) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.roleCode = roleCode;
        this.accessibilityMode = accessibilityMode;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getRoleCode() { return roleCode; }
    public boolean isAccessibilityMode() { return accessibilityMode; }
}

