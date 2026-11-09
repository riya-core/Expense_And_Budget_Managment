package com.expensetracker.service;

import com.expensetracker.dto.AuthUserDto;
import com.expensetracker.dto.LoginRequest;
import com.expensetracker.dto.LoginResponse;
import com.expensetracker.dto.RegisterRequest;
import com.expensetracker.entity.User;
import com.expensetracker.exception.ConflictException;
import com.expensetracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtService jwtService;
    @InjectMocks AuthService authService;

    @Test
    void registerHashesPasswordAndNormalizesEmail() {
        when(userRepository.existsByEmail("riya@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        AuthUserDto dto = authService.register(new RegisterRequest(" Riya ", " Riya@Example.com ", "secret123"));

        assertEquals(1L, dto.id());
        assertEquals("Riya", dto.name());
        assertEquals("riya@example.com", dto.email());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("HASHED", saved.getValue().getPassword());   // never the raw password
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("riya@example.com")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> authService.register(new RegisterRequest("Riya", "riya@example.com", "secret123")));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokenAndUser() {
        User user = User.builder().id(1L).name("Riya").email("riya@example.com").password("HASHED").build();
        when(userRepository.findByEmail("riya@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("riya@example.com")).thenReturn("TOKEN");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = authService.login(new LoginRequest("riya@example.com", "secret123"));

        assertEquals("TOKEN", response.token());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600L, response.expiresIn());
        assertEquals("riya@example.com", response.user().email());
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void loginWithWrongPasswordIssuesNoToken() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("riya@example.com", "wrong")));
        verify(jwtService, never()).generateToken(anyString());
    }
}
