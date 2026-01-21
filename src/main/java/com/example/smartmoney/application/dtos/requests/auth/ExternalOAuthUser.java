package com.example.smartmoney.application.dtos.requests.auth;

public class ExternalOAuthUser {
    private final String email;
    private final String name;
    private final String avatar;

    public ExternalOAuthUser(String email, String name, String avatar) {
        this.email = email;
        this.name = name;
        this.avatar = avatar;
    }

    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getAvatar() { return avatar; }
}
