package com.example.smartmoney.application.ports.cloudinary;

import org.springframework.web.multipart.MultipartFile;

import com.example.smartmoney.application.dtos.responses.SignatureResponse;

public interface CloudinaryPort {

    /**
     * Generate signature for Cloudinary upload
     */

    SignatureResponse sign(String folder);

    /**
     * Upload avatar to Cloudinary and return URL
     */
    String uploadAvatar(MultipartFile file);

    /**
     * Delete avatar from Cloudinary by publicId
     */
    void deleteImage(String publicId, String type);
}