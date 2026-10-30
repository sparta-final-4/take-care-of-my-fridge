package org.example.takecareofmyfridgebe.domain.member.repository;

import jakarta.persistence.LockModeType;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long>, JpaSpecificationExecutor<Member> {

    boolean existsByRole(Member.Role role);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByPhone(String phone);

    boolean existsByNicknameAndIdNot(String nickname, Long id);

    Optional<Member> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") Long id);

    @Query("select m.id from Member m where m.role = :role and m.status = :status")
    List<Long> findIdsByRoleAndStatus(@Param("role") Member.Role role, @Param("status") Member.Status status);
}
