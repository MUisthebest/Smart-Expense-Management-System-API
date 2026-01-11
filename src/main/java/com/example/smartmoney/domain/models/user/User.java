package com.example.smartmoney.domain.models.user;

import com.example.smartmoney.domain.models.user.valueobjects.*;
import com.example.smartmoney.domain.models.user.enums.Role;
import com.example.smartmoney.domain.exceptions.DomainException;
import java.time.LocalDateTime;
import lombok.*;

@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Setter
public class User {
    private Long id;
    private String username;           // biệt danh (20 ký tự)
    private String fullName;           // tên (40 ký tự)
    private Avatar avatar;             // URL ảnh
    private Password password;         // mật khẩu
    private DateOfBirth dateOfBirth;   // ngày sinh
    private Phone phone;               // số điện thoại
    private Email email;               // email
    private Integer coin = 0;          // số coin, mặc định 0
    private Role role = Role.USER;     // vai trò, mặc định USER
    private RefreshToken refreshToken; // refresh token
    private Double rate = 5.0;         // đánh giá sao, mặc định 5.0
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean active = true;
    
    
    // Factory method for registration
    public static User create(
            String username,
            String fullName,
            Avatar avatar,
            Password password,
            DateOfBirth dateOfBirth,
            Phone phone,
            Email email) {
        
        User user = new User();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setAvatar(avatar);
        user.setPassword(password);
        user.setDateOfBirth(dateOfBirth);
        user.setPhone(phone);
        user.setEmail(email);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        
        return user;
    }
    
    // Business methods
    public void updateRefreshToken(RefreshToken refreshToken) {
        this.refreshToken = refreshToken;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void clearRefreshToken() {
        this.refreshToken = null;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void addCoins(int amount) {
        if (amount <= 0) {
            throw new DomainException("Amount must be positive");
        }
        this.coin += amount;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void deductCoins(int amount) {
        if (amount <= 0) {
            throw new DomainException("Amount must be positive");
        }
        if (this.coin < amount) {
            throw new DomainException("Insufficient coins");
        }
        this.coin -= amount;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void updateRate(Double newRate) {
        if (newRate < 0 || newRate > 5) {
            throw new DomainException("Rate must be between 0 and 5");
        }
        this.rate = newRate;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void updateProfile(String fullName, Avatar avatar, DateOfBirth dateOfBirth) {
        if (fullName != null && !fullName.isEmpty()) {
            if (fullName.length() > 40) {
                throw new DomainException("Full name must not exceed 40 characters");
            }
            this.fullName = fullName;
        }
        
        if (avatar != null) {
            this.avatar = avatar;
        }
        
        if (dateOfBirth != null) {
            this.dateOfBirth = dateOfBirth;
        }
        
        this.updatedAt = LocalDateTime.now();
    }

    public void changePassword(String encodedPassword) {
        this.password = Password.fromHash(encodedPassword);
        clearRefreshToken(); // logout all devices
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) {
        if (username == null || username.isEmpty()) {
            throw new DomainException("Username cannot be empty");
        }
        if (username.length() > 20) {
            throw new DomainException("Username must not exceed 20 characters");
        }
        this.username = username;
    }
    
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            throw new DomainException("Full name cannot be empty");
        }
        if (fullName.length() > 40) {
            throw new DomainException("Full name must not exceed 40 characters");
        }
        this.fullName = fullName;
    }
    
    public Avatar getAvatar() { return avatar; }
    public void setAvatar(Avatar avatar) { this.avatar = avatar; }
    
    public Password getPassword() { return password; }
    public void setPassword(Password password) { this.password = password; }
    
    public DateOfBirth getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(DateOfBirth dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    
    public Phone getPhone() { return phone; }
    public void setPhone(Phone phone) { this.phone = phone; }
    
    public Email getEmail() { return email; }
    public void setEmail(Email email) { this.email = email; }
    
    public Integer getCoin() { return coin; }
    public void setCoin(Integer coin) { 
        if (coin < 0) {
            throw new DomainException("Coin cannot be negative");
        }
        this.coin = coin; 
    }

    
    
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    
    public RefreshToken getRefreshToken() { return refreshToken; }
    public void setRefreshToken(RefreshToken refreshToken) { this.refreshToken = refreshToken; }
    
    public Double getRate() { return rate; }
    public void setRate(Double rate) { this.rate = rate; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}