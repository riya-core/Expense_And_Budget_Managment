package com.expensetracker.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-at-least-32-bytes-long";

    @Test
    void generatedTokenCanBeReadBack() {
        JwtService service = new JwtService(SECRET, 60_000);
        String token = service.generateToken("riya@example.com");
        assertEquals(Optional.of("riya@example.com"), service.validateAndGetEmail(token));
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = new JwtService(SECRET, 60_000);
        String token = service.generateToken("riya@example.com");
        char original = token.charAt(25);                      // a character inside the payload
        char changed = original == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, 25) + changed + token.substring(26);
        assertEquals(Optional.empty(), service.validateAndGetEmail(tampered));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = new JwtService(SECRET, -1_000);   // already expired when created
        String token = service.generateToken("riya@example.com");
        assertEquals(Optional.empty(), service.validateAndGetEmail(token));
    }

    @Test
    void tokenFromAnotherSecretIsRejected() {
        JwtService other = new JwtService("a-completely-different-secret-of-32-plus-chars!!", 60_000);
        JwtService service = new JwtService(SECRET, 60_000);
        String token = other.generateToken("riya@example.com");
        assertEquals(Optional.empty(), service.validateAndGetEmail(token));
    }

    @Test
    void garbageIsRejected() {
        JwtService service = new JwtService(SECRET, 60_000);
        assertEquals(Optional.empty(), service.validateAndGetEmail("not-a-token"));
        assertEquals(Optional.empty(), service.validateAndGetEmail(""));
    }

    @Test
    void shortSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtService("short", 1_000));
        assertThrows(IllegalStateException.class, () -> new JwtService("", 1_000));
    }
}
