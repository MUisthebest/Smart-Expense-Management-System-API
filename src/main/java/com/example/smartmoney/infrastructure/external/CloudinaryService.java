package com.example.smartmoney.infrastructure.external;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.infrastructure.config.CloudinaryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {
    
    private final Cloudinary cloudinary;
    private final CloudinaryConfig cloudinaryConfig;
    
    /**
     * Upload avatar image to Cloudinary
     */
    public String uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        
        validateImageFile(file);
        
        try {
            // Generate unique filename
            String filename = "avatar_" + UUID.randomUUID() + "_" + 
                System.currentTimeMillis();
            
            // Upload to Cloudinary
            Map uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                    "folder", cloudinaryConfig.getFolder(),
                    "public_id", filename,
                    "overwrite", true,
                    "resource_type", "image",
                    "transformation", new Object[]{
                        ObjectUtils.asMap("width", 400, "height", 400, "crop", "fill"),
                        ObjectUtils.asMap("quality", "auto"),
                        ObjectUtils.asMap("fetch_format", "auto")
                    }
                )
            );
            
            // Return secure URL
            return (String) uploadResult.get("secure_url");
            
        } catch (IOException e) {
            log.error("Failed to upload image to Cloudinary", e);
            throw new DomainException("Failed to upload image: " + e.getMessage());
        }
    }
    
    /**
     * Delete image from Cloudinary
     */
    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        
        try {
            // Extract public_id from URL
            String publicId = extractPublicIdFromUrl(imageUrl);
            
            if (publicId != null) {
                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                log.info("Deleted image from Cloudinary: {}", publicId);
            }
        } catch (Exception e) {
            log.warn("Failed to delete image from Cloudinary: {}", e.getMessage());
        }
    }
    
    /**
     * Validate image file
     */
    private void validateImageFile(MultipartFile file) {
        // Check file size (max 5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new DomainException("File size exceeds 5MB limit");
        }
        
        // Check content type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new DomainException("Only image files are allowed");
        }
        
        // Check file extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String extension = originalFilename.substring(
                originalFilename.lastIndexOf(".") + 1).toLowerCase();
            if (!List.of("jpg", "jpeg", "png", "gif", "webp").contains(extension)) {
                throw new DomainException("Unsupported file format. Use JPG, PNG, GIF, or WebP");
            }
        }
    }
    
    /**
     * Extract public_id from Cloudinary URL
     */
    private String extractPublicIdFromUrl(String url) {
        try {
            // URL format: https://res.cloudinary.com/cloudname/image/upload/v1234567890/folder/filename.jpg
            String[] parts = url.split("/upload/");
            if (parts.length < 2) return null;
            
            String path = parts[1];
            // Remove version prefix if exists
            if (path.startsWith("v")) {
                path = path.substring(path.indexOf("/") + 1);
            }
            
            // Remove file extension
            int dotIndex = path.lastIndexOf(".");
            if (dotIndex != -1) {
                path = path.substring(0, dotIndex);
            }
            
            return path;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get default avatar URL
     */
    public String getDefaultAvatar() {
        return "https://res.cloudinary.com/" + 
               cloudinaryConfig.getCloudName() + 
               "/image/upload/" + 
               cloudinaryConfig.getFolder() + 
               "/default_avatar.png";
    }
    
    /**
     * Upload any file to Cloudinary
     */
    public String uploadFile(MultipartFile file, String folder) {
        try {
            Map uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "auto"
                )
            );
            return (String) uploadResult.get("secure_url");
        } catch (IOException e) {
            throw new DomainException("Failed to upload file: " + e.getMessage());
        }
    }
}