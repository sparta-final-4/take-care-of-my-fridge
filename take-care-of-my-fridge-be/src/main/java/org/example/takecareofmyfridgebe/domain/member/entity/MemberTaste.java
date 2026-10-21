package org.example.takecareofmyfridgebe.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

@Entity
@Table(name = "member_tastes", uniqueConstraints =
        @UniqueConstraint(name = "uk_member_tastes_member_taste", columnNames = {"member_id", "taste"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberTaste extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Member.Taste taste;

    @Builder(access = AccessLevel.PACKAGE)
    private MemberTaste(Member member, Member.Taste taste) {
        this.member = member;
        this.taste = taste;
    }
}
