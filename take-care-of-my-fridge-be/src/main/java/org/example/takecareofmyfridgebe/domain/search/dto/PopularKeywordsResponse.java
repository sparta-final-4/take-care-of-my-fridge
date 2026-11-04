package org.example.takecareofmyfridgebe.domain.search.dto;

import java.util.List;

/**
 * 인기 검색어
 *
 * @param keywords 오늘 많이 검색된 순서. 없으면 빈 목록
 */
public record PopularKeywordsResponse(
        List<String> keywords
) {
}
