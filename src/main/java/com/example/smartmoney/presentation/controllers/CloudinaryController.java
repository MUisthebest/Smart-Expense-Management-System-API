package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.ImageResponse;
import com.example.smartmoney.application.usecases.cloudinary.GetImageUseCase;
import com.example.smartmoney.application.usecases.cloudinary.DeleteImageUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cloudinary")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Cloudinary",
    description = "Cloudinary image management APIs for retrieving image data"
)
public class CloudinaryController {

    private final GetImageUseCase getImageUseCase;
    private final DeleteImageUseCase deleteImageUseCase;

    @Operation(
        summary = "Get image URL by public ID",
        description = "Retrieve the full image URL and metadata from Cloudinary using the public ID"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Image URL retrieved successfully",
            content = @Content(schema = @Schema(implementation = CheckResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid public ID"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Server error while retrieving image"
        )
    })
    @GetMapping("/image/{publicId}")
    public ResponseEntity<CheckResponse<ImageResponse>> getImage(
            @Parameter(description = "Public ID of the image in Cloudinary", required = true)
            @PathVariable String publicId) {

        try {
            if (publicId == null || publicId.trim().isEmpty()) {
                log.warn("Invalid public ID provided: {}", publicId);
                CheckResponse<ImageResponse> response = CheckResponse.<ImageResponse>builder()
                    .success(false)
                    .message("Public ID cannot be empty")
                    .build();
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            log.info("Retrieving image URL for publicId: {}", publicId);
            ImageResponse imageResponse = getImageUseCase.execute(publicId);

            CheckResponse<ImageResponse> response = CheckResponse.<ImageResponse>builder()
                .success(true)
                .message("Image URL retrieved successfully")
                .data(imageResponse)
                .build();

            log.info("Image URL retrieved successfully for publicId: {}", publicId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to retrieve image URL for publicId: {}", publicId, e);

            CheckResponse<ImageResponse> response = CheckResponse.<ImageResponse>builder()
                .success(false)
                .message("Failed to retrieve image: " + e.getMessage())
                .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Operation(
        summary = "Delete image by public ID",
        description = "Delete an image from Cloudinary using public ID"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Image deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid public ID"),
        @ApiResponse(responseCode = "500", description = "Server error while deleting image")
    })
    @DeleteMapping("/image")
    public ResponseEntity<CheckResponse<Void>> deleteImage(@RequestBody Map<String, String> body) {

        String publicId = body.get("publicId");

        try {
            if (publicId == null || publicId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                    CheckResponse.<Void>builder()
                        .success(false)
                        .message("Public ID cannot be empty")
                        .build()
                );
            }

            log.info("Deleting image with publicId: {}", publicId);

            deleteImageUseCase.execute(publicId); // 👈 gọi usecase

            return ResponseEntity.ok(
                CheckResponse.<Void>builder()
                    .success(true)
                    .message("Image deleted successfully")
                    .build()
            );

        } catch (Exception e) {
            log.error("Delete failed for publicId: {}", publicId, e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                CheckResponse.<Void>builder()
                    .success(false)
                    .message("Delete failed: " + e.getMessage())
                    .build()
            );
        }
    }
}
