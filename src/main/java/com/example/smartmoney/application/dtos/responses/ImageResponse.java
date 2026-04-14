package com.example.smartmoney.application.dtos.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Image response from Cloudinary")
public class ImageResponse {

    @Schema(description = "Public ID of the image in Cloudinary", example = "avatar_12345-67890_1234567890")
    private String publicId;

    @Schema(description = "Full URL of the image", example = "https://res.cloudinary.com/cloud-name/image/upload/folder/avatar_12345-67890_1234567890")
    private String url;

    @Schema(description = "Cloud name configured for Cloudinary", example = "my-cloud-name")
    private String cloudName;

    @Schema(description = "Folder path where image is stored", example = "smartmoney/avatars")
    private String folder;
}
