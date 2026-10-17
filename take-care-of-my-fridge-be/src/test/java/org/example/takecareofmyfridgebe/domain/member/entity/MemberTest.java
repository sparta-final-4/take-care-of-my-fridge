package org.example.takecareofmyfridgebe.domain.member.entity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTest {

    @Test
    void createInitializesAccountAndLinksSelectedTastes() {
        Member member = Member.builder()
                .email("member@example.com")
                .passwordHash("encoded-password")
                .nickname("member")
                .tastes(Set.of(Member.Taste.SALTY, Member.Taste.SWEET))
                .build();

        assertThat(member.getRole()).isEqualTo(Member.Role.USER);
        assertThat(member.getStatus()).isEqualTo(Member.Status.ACTIVE);
        assertThat(member.getTastes()).extracting(MemberTaste::getTaste)
                .containsExactlyInAnyOrder(Member.Taste.SALTY, Member.Taste.SWEET);
        assertThat(member.getTastes()).allSatisfy(taste -> assertThat(taste.getMember()).isSameAs(member));
    }
}
