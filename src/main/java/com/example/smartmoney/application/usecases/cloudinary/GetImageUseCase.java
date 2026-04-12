package com.example.smartmoney.application.usecases.cloudinary;

import com.example.smartmoney.application.dtos.responses.ImageResponse;
import com.example.smartmoney.infrastructure.integration.cloudinary.CloudinaryService;
import com.example.smartmoney.infrastructure.integration.cloudinary.CloudinaryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetImageUseCase {

    private final CloudinaryService cloudinaryService;
    private final CloudinaryConfig cloudinaryConfig;

    public ImageResponse execute(String publicId) {
        log.info("Getting image URL for publicId: {}", publicId);

        String imageUrl = buildImageUrl(publicId);
        
        ImageResponse response = ImageResponse.builder()
            .publicId(publicId)
            .url(imageUrl)
            .cloudName(cloudinaryConfig.getCloudName())
            .folder(cloudinaryConfig.getFolder())
            .build();

        log.info("Image URL retrieved successfully for publicId: {}", publicId);
        return response;
    }

    private String buildImageUrl(String publicId) {
        return String.format("https://res.cloudinary.com/%s/image/upload/%s/%s",
            cloudinaryConfig.getCloudName(),
            cloudinaryConfig.getFolder(),
            publicId);
    }
}
