package org.example.takecareofmyfridgebe.infra.s3.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ImageUploadUrlRequest(
        @NotBlank(message = "이미지 형식을 입력해 주세요.")
        String contentType,

        @NotNull(message = "파일 크기를 입력해 주세요.")
        @Positive(message = "빈 파일은 올릴 수 없습니다.")
        @Max(value = MAX_FILE_SIZE, message = "이미지는 5MB 이하만 올릴 수 있습니다.")
        Long fileSize
) {

    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
}
