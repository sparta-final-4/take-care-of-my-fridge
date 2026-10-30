package org.example.takecareofmyfridgebe.infra.s3.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.example.takecareofmyfridgebe.infra.s3.dto.ImageUploadUrlRequest;
import org.example.takecareofmyfridgebe.infra.s3.dto.ImageUploadUrlResponse;
import org.example.takecareofmyfridgebe.infra.s3.service.ImageUploadUrlService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/images")
@RequiredArgsConstructor
public class AdminImageController {

    private final ImageUploadUrlService imageUploadUrlService;

    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<ImageUploadUrlResponse>> createProductImageUploadUrl(
            @Valid @RequestBody ImageUploadUrlRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(imageUploadUrlService.createProductImageUploadUrl(request)));
    }
}
