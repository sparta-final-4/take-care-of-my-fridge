package org.example.takecareofmyfridgebe.infra.s3.service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.infra.s3.config.ImageStorageProperties;
import org.example.takecareofmyfridgebe.infra.s3.dto.ImageUploadUrlRequest;
import org.example.takecareofmyfridgebe.infra.s3.dto.ImageUploadUrlResponse;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 상품 이미지 Presigned PUT URL 을 만든다. 파일은 서버를 거치지 않고 브라우저가 S3 에 직접 올린다.
 * Content-Type 과 Content-Length 를 서명에 넣어, 요청과 다른 형식·크기의 파일은 S3 가 거부한다.
 */
@Service
@RequiredArgsConstructor
public class ImageUploadUrlService {

    static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(5);
    static final String PRODUCT_IMAGE_PATH = "images/products/";

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final S3Presigner s3Presigner;
    private final ImageStorageProperties properties;

    public ImageUploadUrlResponse createProductImageUploadUrl(ImageUploadUrlRequest request) {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_DISABLED);
        }
        String extension = EXTENSIONS.get(request.contentType());
        if (extension == null) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_TYPE);
        }

        // 매번 새 키라 같은 URL 의 내용이 바뀌지 않는다(CloudFront 캐시 무효화 불필요)
        String path = PRODUCT_IMAGE_PATH + UUID.randomUUID() + "." + extension;
        PutObjectRequest putObject = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(properties.keyPrefix() + "/" + path)
                .contentType(request.contentType())
                .contentLength(request.fileSize())
                .build();
        String uploadUrl = s3Presigner.presignPutObject(presign -> presign
                        .signatureDuration(UPLOAD_URL_TTL)
                        .putObjectRequest(putObject))
                .url()
                .toString();

        return new ImageUploadUrlResponse(uploadUrl, stripTrailingSlash(properties.publicBaseUrl()) + "/" + path);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
