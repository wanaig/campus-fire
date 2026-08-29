package com.campusfire.auth;

public class UserAccount {
    private final Long id;
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final String roleCode;
    private final boolean enabled;
    private final boolean accessibilityMode;

    public UserAccount(Long id, String username, String displayName, String passwordHash,
                       String roleCode, boolean enabled, boolean accessibilityMode) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.roleCode = roleCode;
        this.enabled = enabled;
        this.accessibilityMode = accessibilityMode;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public String getRoleCode() { return roleCode; }
    public boolean isEnabled() { return enabled; }
    public boolean isAccessibilityMode() { return accessibilityMode; }
}

