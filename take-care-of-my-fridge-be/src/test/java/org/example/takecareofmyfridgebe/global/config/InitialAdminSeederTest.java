package org.example.takecareofmyfridgebe.global.config;

import jakarta.validation.Validator;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialAdminSeederTest {

    @Mock
    private Environment environment;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private Validator validator;
    @Mock
    private ApplicationArguments arguments;
    @InjectMocks
    private InitialAdminSeeder seeder;

    @Test
    void noBootstrapSettingsLeaveDatabaseUntouched() {
        seeder.run(arguments);

        verifyNoInteractions(memberRepository, passwordEncoder);
    }

    @Test
    void createsOnePermanentAdminWithEncodedPassword() {
        configuredCredentials();
        when(passwordEncoder.encode("Strongpass1!")).thenReturn("encoded-password");
        when(memberRepository.existsByRole(Member.Role.ADMIN)).thenReturn(false, true);

        seeder.run(arguments);
        seeder.run(arguments);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveAndFlush(captor.capture());
        Member admin = captor.getValue();
        assertEquals(Member.Role.ADMIN, admin.getRole());
        assertEquals(Member.Status.ACTIVE, admin.getStatus());
        assertEquals("admin@example.com", admin.getEmail());
        assertEquals("encoded-password", admin.getPasswordHash());
        assertNull(admin.getPhone());
    }

    @Test
    void existingAdminIsNeverOverwritten() {
        configuredCredentials();
        when(memberRepository.existsByRole(Member.Role.ADMIN)).thenReturn(true);

        seeder.run(arguments);

        verify(memberRepository, never()).saveAndFlush(any(Member.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void existingUserEmailIsNotPromotedToAdmin() {
        configuredCredentials();
        when(memberRepository.existsByEmail("admin@example.com")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> seeder.run(arguments));

        verify(memberRepository, never()).saveAndFlush(any(Member.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void incompleteBootstrapSettingsFailWithoutCreatingAccount() {
        when(environment.getProperty("ADMIN_BOOTSTRAP_EMAIL")).thenReturn("admin@example.com");

        assertThrows(IllegalStateException.class, () -> seeder.run(arguments));

        verifyNoInteractions(memberRepository, passwordEncoder);
    }

    private void configuredCredentials() {
        when(environment.getProperty("ADMIN_BOOTSTRAP_EMAIL")).thenReturn("admin@example.com");
        when(environment.getProperty("ADMIN_BOOTSTRAP_PASSWORD")).thenReturn("Strongpass1!");
        when(environment.getProperty("ADMIN_BOOTSTRAP_NICKNAME")).thenReturn("운영자");
    }
}
