package com.example.smartmoney.application.usecases.cloudinary;

import com.example.smartmoney.application.dtos.responses.SignatureResponse;
import com.example.smartmoney.application.ports.cloudinary.CloudinaryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GenerateSignatureUseCase {

    private final CloudinaryPort cloudinaryPort;

    public SignatureResponse execute(String folder) {
        SignatureResponse result = cloudinaryPort.sign(folder);

        return SignatureResponse.builder()
            .timestamp(result.getTimestamp())
            .signature(result.getSignature())
            .apiKey(result.getApiKey())
            .cloudName(result.getCloudName())
            .build();
    }
}