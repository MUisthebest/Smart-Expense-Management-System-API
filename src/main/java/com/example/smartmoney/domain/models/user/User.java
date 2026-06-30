package com.example.smartmoney.domain.models.user;

import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import com.example.smartmoney.domain.models.user.valueobjects.*;
import com.example.smartmoney.domain.models.user.enums.Role;
import com.example.smartmoney.domain.exceptions.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import jdk.jshell.spi.ExecutionControl;
import lombok.*;

@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Setter
public class User {
    private UUID id;
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
    private boolean emailVerified = false;  // Trạng thái xác minh email
    private boolean notificationEnabled = true;
    private BigDecimal income;
    private Focus_Mode focusMode;    //New attributes
    private Intervention_Level interventionLevel;   //New attributes
    private Saving_Pace savingPace;  //New attributes.
    
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
    
    // Factory method for Google OAuth registration
    public static User createGoogleUser(
            String username,
            String email,
            String fullName,
            Avatar avatarUrl) {
        
        User user = new User();
        user.setUsername(username);
        user.setEmail(new Email(email));
        user.setFullName(fullName);
        user.setAvatar(avatarUrl);
        // Google users don't have a password initially
        user.setPassword(Password.fromHash("SOCIAL_LOGIN")); // Empty password for OAuth users
        // set enough 18 years old as default DOB        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String dob = LocalDate.now().minusYears(18).format(formatter).toString();
        user.setDateOfBirth(new DateOfBirth(dob));
        user.setPhone(new Phone("0123456789"));
        user.setRole(Role.USER);
        user.setCoin(0);
        user.setRate(5.0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setActive(true);
        user.setEmailVerified(true);  // OAuth users have verified emails
        
        return user;
    }
    
    // Factory method for Facebook OAuth registration
    public static User createFacebookUser(
            String username,
            String email,
            String fullName,
            Avatar avatarUrl) {
        
        User user = new User();
        user.setUsername(username);
        user.setEmail(new Email(email));
        user.setFullName(fullName);
        user.setAvatar(avatarUrl);
        // Facebook users don't have a password initially
        user.setPassword(Password.fromHash("SOCIAL_LOGIN")); // Empty password for OAuth users
        // set enough 18 years old as default DOB        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String dob = LocalDate.now().minusYears(18).format(formatter).toString();
        user.setDateOfBirth(new DateOfBirth(dob));
        user.setPhone(new Phone("0123456789"));
        user.setRole(Role.USER);
        user.setCoin(0);
        user.setRate(5.0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setActive(true);
        user.setEmailVerified(true);  // OAuth users have verified emails
        
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
    
    public void activate() {
        this.active = true;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void deactivate() {
        this.active = false;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void verifyEmail() {
        this.emailVerified = true;
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) {
        if (username == null || username.isEmpty()) {
            throw new DomainException("Username cannot be empty");
        }
        if (username.length() > 30) {
            throw new DomainException("Username must not exceed 30 characters");
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
    
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public void enableNotification() {
    this.notificationEnabled = true;
    this.updatedAt = LocalDateTime.now();
    }   

    public void disableNotification() {
        this.notificationEnabled = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateFinancialSetup(
            BigDecimal income,
            Saving_Pace savingPace,
            Intervention_Level interventionLevel,
            Focus_Mode focusMode
    ) {
        if (income == null || income.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("Income must be greater than zero");
        }

        if (savingPace == null || interventionLevel == null || focusMode == null) {
            throw new DomainException("Financial setup information is required");
        }

        this.income = income;
        this.savingPace = savingPace;
        this.interventionLevel = interventionLevel;
        this.focusMode = focusMode;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean hasCompletedFinancialSetup() {
        return income != null
                && savingPace != null
                && interventionLevel != null
                && focusMode != null;
    }

    public void clearFinancialSetup() {
        this.income = null;
        this.savingPace = null;
        this.interventionLevel = null;
        this.focusMode = null;
        this.updatedAt = LocalDateTime.now();
    }
}