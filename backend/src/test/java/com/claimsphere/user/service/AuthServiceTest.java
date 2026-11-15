package com.claimsphere.user.service;

import com.claimsphere.audit.service.AuditService;
import com.claimsphere.common.domain.Market;
import com.claimsphere.common.exception.ConflictException;
import com.claimsphere.security.JwtTokenService;
import com.claimsphere.user.repository.ClaimantProfileRepository;
import com.claimsphere.user.domain.Role;
import com.claimsphere.user.domain.User;
import com.claimsphere.user.dto.LoginRequest;
import com.claimsphere.user.dto.RegisterRequest;
import com.claimsphere.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private ClaimantProfileRepository claimantProfileRepository;

    @Mock
    private AuditService auditService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(userRepository, claimantProfileRepository, passwordEncoder,
                auditService);
        authService = new AuthService(userService, userRepository, passwordEncoder, jwtTokenService,
                auditService);
    }

    @Test
    void registerAlwaysCreatesClaimantWithHashedPasswordAndNormalisedEmail() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(new RegisterRequest("  Jane@Example.com ", "password123", "Jane Doe", Market.SINGAPORE, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CLAIMANT);
        assertThat(saved.getValue().getEmail()).isEqualTo("jane@example.com");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("jane@example.com", "password123", "Jane", Market.SINGAPORE, null)))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginFailsWithWrongPassword() {
        User user = new User("jane@example.com", passwordEncoder.encode("password123"), "Jane", Role.CLAIMANT);
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwtTokenService, never()).issueToken(any());
    }

    @Test
    void loginFailsForUnknownEmailWithSameError() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "password123")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
