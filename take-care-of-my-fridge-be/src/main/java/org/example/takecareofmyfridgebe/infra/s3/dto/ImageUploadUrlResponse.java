package org.example.takecareofmyfridgebe.infra.s3.dto;

/**
 * @param uploadUrl 브라우저가 파일을 PUT 할 Presigned URL. 요청한 Content-Type 과 크기 그대로 보내야 한다
 * @param imageUrl  업로드 후 상품 imageUrl 에 저장할 조회 URL
 */
public record ImageUploadUrlResponse(
        String uploadUrl,
        String imageUrl
) {
}
