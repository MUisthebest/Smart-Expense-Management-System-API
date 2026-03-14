package com.example.smartmoney.application.usecases.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

import com.example.smartmoney.application.ports.cloudinary.CloudinaryPort;
import com.example.smartmoney.application.dtos.responses.UserResponse;
import com.example.smartmoney.application.dtos.requests.auth.UpdateUserRequest;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UpdateUserUseCase {

    private final UserRepository userRepository;

    private final CloudinaryPort cloudinaryPort;

    public UserResponse execute(UUID userId, UpdateUserRequest request) {
        log.info("Updating user with id: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new DomainException("User not found"));

        // Update user information
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }


        if (request.getAvatar() != null) {
            String avatarUrl = cloudinaryPort.uploadAvatar(request.getAvatar());
            if (user.getAvatar() != null && user.getAvatar().getUrl() != null) {
                cloudinaryPort.deleteImage(user.getAvatar().getUrl());
            }
            user.setAvatar(new Avatar(avatarUrl));
        }

        if (request.getDateOfBirth() != null) {
            user.setDateOfBirth(new DateOfBirth(request.getDateOfBirth().toString()));
        }

        if (request.getPhone() != null) {
            // Check if phone is already used by another user
            Phone newPhone = new Phone(request.getPhone());
            if (userRepository.existsByPhoneAndIdNot(newPhone, userId)) {
                throw new DomainException("Phone number already in use");
            }
            user.setPhone(newPhone);
        }

        // If email needs to be updated, it should go through verification process
        // This is only for basic info updates

        User updatedUser = userRepository.save(user);
        log.info("User updated successfully: {}", userId);

        return mapToUserResponse(updatedUser);
    }

    private UserResponse mapToUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setFullName(user.getFullName());
        response.setAvatar(user.getAvatar() != null ? user.getAvatar().getUrl() : null);
        response.setDateOfBirth(user.getDateOfBirth() != null ? 
            user.getDateOfBirth().getFormattedValue() : null);
        response.setPhone(user.getPhone() != null ? user.getPhone().getValue() : null);
        response.setEmail(user.getEmail().getValue());
        response.setCoin(user.getCoin());
        response.setRole(user.getRole());
        response.setRate(user.getRate());
        response.setActive(user.isActive());
        return response;
    }
}