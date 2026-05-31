package com.example.smartmoney.application.usecases.cloudinary;

import com.example.smartmoney.infrastructure.integration.cloudinary.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteImageUseCase {

    private final CloudinaryService cloudinaryService;

    public void execute(String publicId, String type) {
        log.info("Deleting image with publicId: {}", publicId);

        if (publicId == null || publicId.trim().isEmpty()) {
            throw new IllegalArgumentException("Public ID cannot be empty");
        }

        cloudinaryService.deleteImage(publicId, type);
        log.info("Image deleted successfully with publicId: {}", publicId);
    }
}
