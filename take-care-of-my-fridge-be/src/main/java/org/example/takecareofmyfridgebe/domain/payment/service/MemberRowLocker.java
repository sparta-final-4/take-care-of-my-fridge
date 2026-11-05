package org.example.takecareofmyfridgebe.domain.payment.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;

// 같은 회원의 카드·결제 비밀번호 변경을 하나씩 처리하도록 호출한 트랜잭션 안에서 회원 행을 잠근다.
// 트랜잭션의 첫 조회로 호출해야 뒤의 조회가 잠근 뒤의 최신 값을 읽는다(REPEATABLE READ)
@Component
@RequiredArgsConstructor
public class MemberRowLocker {

    private final MemberRepository memberRepository;

    public Member lock(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
