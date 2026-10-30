package org.example.takecareofmyfridgebe.infra.s3.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 상품 이미지 버킷 설정. 배포 서버는 Parameter Store(IMAGE_*) 값을 쓴다.
 *
 * @param bucket        S3 버킷 이름. 비어 있으면 업로드를 쓰지 않는다(로컬)
 * @param keyPrefix     환경 prefix(dev, main). CloudFront 가 이 prefix 를 origin_path 로 붙여 읽는다
 * @param publicBaseUrl 이미지 조회 기준 URL(프론트 도메인). 그 아래 /images/* 로 서빙된다
 */
@ConfigurationProperties(prefix = "app.image")
public record ImageStorageProperties(
        String bucket,
        String keyPrefix,
        String publicBaseUrl
) {

    public boolean enabled() {
        return bucket != null && !bucket.isBlank();
    }
}
