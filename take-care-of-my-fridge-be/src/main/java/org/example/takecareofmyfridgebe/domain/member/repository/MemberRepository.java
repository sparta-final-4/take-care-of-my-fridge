package org.example.takecareofmyfridgebe.domain.member.repository;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByRole(Member.Role role);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, Long id);

    Optional<Member> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") Long id);

    Page<Member> findByEmailContainingIgnoreCase(String email, Pageable pageable);

    Page<Member> findByRole(Member.Role role, Pageable pageable);

    Page<Member> findByEmailContainingIgnoreCaseAndRole(String email, Member.Role role, Pageable pageable);
}
