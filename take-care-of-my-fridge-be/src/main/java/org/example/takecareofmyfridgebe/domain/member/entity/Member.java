package org.example.takecareofmyfridgebe.domain.member.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_members_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_members_nickname", columnNames = "nickname")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    public enum Role { USER, ADMIN }
    public enum Status { ACTIVE, SUSPENDED }
    public enum Taste { SALTY, SWEET, SPICY, UMAMI, MILD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemberTaste> tastes = new ArrayList<>();

    private Member(String email, String passwordHash, String nickname, Set<Taste> tastes) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.role = Role.USER;
        this.status = Status.ACTIVE;
        tastes.forEach(taste -> this.tastes.add(MemberTaste.create(this, taste)));
    }

    public static Member create(String email, String passwordHash, String nickname, Set<Taste> tastes) {
        return new Member(email, passwordHash, nickname, tastes);
    }

    public static Member createInitialAdmin(String email, String passwordHash, String nickname) {
        Member member = new Member(email, passwordHash, nickname, Set.of());
        member.role = Role.ADMIN;
        return member;
    }

    public void replaceTastes(Set<Taste> newTastes) {
        tastes.removeIf(existing -> !newTastes.contains(existing.getTaste()));
        for (Taste taste : newTastes) {
            if (tastes.stream().noneMatch(existing -> existing.getTaste() == taste)) {
                tastes.add(MemberTaste.create(this, taste));
            }
        }
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
