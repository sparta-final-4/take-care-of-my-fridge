package org.example.takecareofmyfridgebe.infra.s3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.infra.s3.config.ImageStorageProperties;
import org.example.takecareofmyfridgebe.infra.s3.dto.ImageUploadUrlRequest;
import org.example.takecareofmyfridgebe.infra.s3.dto.ImageUploadUrlResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class ImageUploadUrlServiceTest {

    // 서명은 로컬 계산이라 네트워크·실제 자격 증명 없이 URL 을 검증할 수 있다
    private final S3Presigner presigner = S3Presigner.builder()
            .region(Region.AP_NORTHEAST_2)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret")))
            .build();

    @AfterEach
    void closePresigner() {
        presigner.close();
    }

    @Test
    void createsSignedPutUrlUnderEnvPrefixAndMatchingPublicUrl() {
        ImageUploadUrlService service = new ImageUploadUrlService(presigner,
                new ImageStorageProperties("imhungry-images-1", "dev", "https://dev.imhungry.click/"));

        ImageUploadUrlResponse response = service.createProductImageUploadUrl(
                new ImageUploadUrlRequest("image/png", 1024L));

        URI uploadUrl = URI.create(response.uploadUrl());
        assertThat(uploadUrl.getHost()).startsWith("imhungry-images-1.s3.ap-northeast-2.amazonaws.com");
        assertThat(uploadUrl.getPath()).matches("/dev/images/products/[0-9a-f-]{36}\\.png");
        assertThat(uploadUrl.getQuery())
                .contains("X-Amz-Expires=300")
                .contains("X-Amz-SignedHeaders=content-length;content-type;host");

        String key = uploadUrl.getPath().substring("/dev/".length());
        assertThat(response.imageUrl()).isEqualTo("https://dev.imhungry.click/" + key);
    }

    @Test
    void rejectsUnsupportedContentType() {
        ImageUploadUrlService service = new ImageUploadUrlService(presigner,
                new ImageStorageProperties("imhungry-images-1", "dev", "https://dev.imhungry.click"));

        assertThatThrownBy(() -> service.createProductImageUploadUrl(new ImageUploadUrlRequest("image/gif", 1024L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_IMAGE_TYPE);
    }

    @Test
    void disabledWhenBucketIsBlank() {
        ImageUploadUrlService service = new ImageUploadUrlService(presigner,
                new ImageStorageProperties("", "local", ""));

        assertThatThrownBy(() -> service.createProductImageUploadUrl(new ImageUploadUrlRequest("image/png", 1024L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_UPLOAD_DISABLED);
    }
}
