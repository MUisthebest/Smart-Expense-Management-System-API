package com.example.smartmoney.infrastructure.integration.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.application.dtos.responses.SignatureResponse;
import com.example.smartmoney.application.ports.cloudinary.CloudinaryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.cloudinary.Transformation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService implements CloudinaryPort {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final String DEFAULT_AVATAR_FILENAME = "default_avatar.png";
    private static final Pattern PUBLIC_ID_PATTERN = Pattern.compile("/upload/(?:v\\d+/)?(.+?)(?:\\.[a-z]+)?$");

    private final Cloudinary cloudinary;
    private final CloudinaryConfig cloudinaryConfig;

    @Override
    public SignatureResponse sign(String folder) {
        try {
            long timestamp = System.currentTimeMillis() / 1000;

            String dataToSign =
                "folder=" + folder +
                "&timestamp=" + timestamp +
                cloudinaryConfig.getApiSecret(); 

            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(dataToSign.getBytes());

            StringBuilder signature = new StringBuilder();
            for (byte b : digest) {
                signature.append(String.format("%02x", b));
            }

            return SignatureResponse.builder()
                .timestamp(timestamp)
                .signature(signature.toString())
                .apiKey(cloudinaryConfig.getApiKey())      // 👈 thêm getter
                .cloudName(cloudinaryConfig.getCloudName())
                .build();

        } catch (Exception e) {
            throw new RuntimeException("Cloudinary sign failed", e);
        }
    }


    @Override
    public String uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.debug("Avatar file is empty, returning null");
            return null;
        }

        FileValidator.validate(file);

        String publicId = generatePublicId("avatar");
        Map<?, ?> uploadResult = uploadToCloudinary(file, publicId, true);
        String secureUrl = (String) uploadResult.get("secure_url");

        log.info("Avatar uploaded successfully, publicId: {}", publicId);
        return secureUrl;
    }

    @Override
    public void deleteImage(String publicId, String type) {
        if (publicId == null || publicId.isBlank()) return;

        String resourceType = "image";

        if ("voice".equals(type)) {
            resourceType = "video"; // 👈 audio = video
        }

        try {
            Map result = cloudinary.uploader().destroy(
                publicId,
                ObjectUtils.asMap("resource_type", resourceType)
            );

            log.info("🔥 Delete result: {}", result);

        } catch (Exception e) {
            log.warn("Delete failed: {}", e.getMessage());
        }
    }
    
    /**
     * Returns the default avatar URL using configured cloud name and folder.
     */
    public String getDefaultAvatar() {
        return String.format("https://res.cloudinary.com/%s/image/upload/%s/%s",
                cloudinaryConfig.getCloudName(),
                cloudinaryConfig.getFolder(),
                DEFAULT_AVATAR_FILENAME);
    }

    /**
     * Uploads any file to a specified folder. Minimal validation (only checks emptiness).
     */
    public String uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("File is empty");
        }

        String publicId = generatePublicId("file");
        Map<?, ?> uploadResult = uploadToCloudinary(file, publicId, false);
        return (String) uploadResult.get("secure_url");
    }

    // ------------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------------

    private String generatePublicId(String prefix) {
        return prefix + "_" + UUID.randomUUID() + "_" + System.currentTimeMillis();
    }

    private Map<?, ?> uploadToCloudinary(MultipartFile file, String publicId, boolean isAvatar) {
        try {
            byte[] fileBytes = file.getBytes(); // An toàn với file ≤5MB

            Map<String, Object> options = ObjectUtils.asMap(
                "folder", cloudinaryConfig.getFolder(),
                "public_id", publicId,
                "overwrite", true,
                "resource_type", "auto"
            );

            if (isAvatar) {
                // Sử dụng Transformation builder để tạo cấu hình chuẩn
                Transformation transformation = new Transformation()
                    .width(400)
                    .height(400)
                    .crop("fill")
                    .gravity("face")
                    .quality("auto")
                    .fetchFormat("auto");
                    
                options.put("transformation", transformation);
            }

            return cloudinary.uploader().upload(fileBytes, options);
        } catch (IOException e) {
            log.error("Failed to upload file to Cloudinary, publicId: {}", publicId, e);
            throw new DomainException("Failed to upload file: " + e.getMessage());
        }
    }
    private java.util.Optional<String> extractPublicIdFromUrl(String url) {
        Matcher matcher = PUBLIC_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return java.util.Optional.of(matcher.group(1));
        }
        return java.util.Optional.empty();
    }

    // ------------------------------------------------------------------------
    // Inner validator class (could be extracted to a separate file if needed)
    // ------------------------------------------------------------------------
    private static final class FileValidator {
        static void validate(MultipartFile file) {
            // Size check
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new DomainException("File size exceeds 5MB limit");
            }

            // Content type check
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new DomainException("Only image files are allowed");
            }

            // Extension check
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                throw new DomainException("File name is missing");
            }

            String extension = extractExtension(originalFilename);
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new DomainException("Unsupported file format. Allowed: " + ALLOWED_EXTENSIONS);
            }
        }

        private static String extractExtension(String filename) {
            int dotIndex = filename.lastIndexOf('.');
            if (dotIndex == -1 || dotIndex == filename.length() - 1) {
                throw new DomainException("Invalid file name (no extension)");
            }
            return filename.substring(dotIndex + 1).toLowerCase();
        }
    }
}