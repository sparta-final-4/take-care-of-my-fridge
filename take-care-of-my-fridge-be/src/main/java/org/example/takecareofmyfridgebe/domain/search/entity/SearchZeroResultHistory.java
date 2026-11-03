package org.example.takecareofmyfridgebe.domain.search.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

// 결과가 0건이었던 통합검색 기록. 동의어·사전에 넣을 검색어를 찾는 데 쓴다. 검색 시각은 created_at이다.
@Entity
@Table(
        name = "search_zero_result_histories",
        indexes = {
                @Index(name = "idx_search_zero_result_histories_created_at", columnList = "created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchZeroResultHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target", nullable = false, length = 20)
    private SearchTarget target;

    @Column(name = "keyword", nullable = false, length = 50)
    private String keyword;

    public SearchZeroResultHistory(SearchTarget target, String keyword) {
        this.target = target;
        this.keyword = keyword;
    }
}
