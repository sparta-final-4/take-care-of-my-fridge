package org.example.takecareofmyfridgebe.infra.elasticsearch.index;

import org.example.takecareofmyfridgebe.infra.elasticsearch.dto.EsDocument;

import java.util.List;
import java.util.Optional;

/**
 * ES 인덱스에 넣을 문서를 원본 DB에서 읽는다. 인덱스(레시피, 상품 등)마다 하나씩 구현한다.
 * <br>
 * {@link EsDocument#id()}는 ES 문서 id로, {@link EsDocument#source()}는 문서 본문으로 쓴다.
 */
public interface EsDocumentReader {

    // 전체 재색인용: 모든 문서
    List<EsDocument> readAll();

    // 단건 색인용: 원본이 삭제됐으면 빈 Optional
    Optional<EsDocument> readOne(long id);

}
