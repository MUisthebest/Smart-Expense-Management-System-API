package com.example.smartmoney.application.ports.cloudinary;

import org.springframework.web.multipart.MultipartFile;

public interface CloudinaryPort {

    /**
     * Upload avatar to Cloudinary and return URL
     */
    String uploadAvatar(MultipartFile file);

    /**
     * Delete avatar from Cloudinary by publicId
     */
    void deleteImage(String publicId);
}