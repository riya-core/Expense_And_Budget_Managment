package com.expensetracker.util;

import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Who is logged in? Resolves the JWT principal (the email) to the User row.
 * The contract lists this helper under P2, but P1 needs it now. Both use the same method
 * names, get() and id(), so P2 can simply keep this file instead of writing their own.
 * Inject it as a bean: private final CurrentUser currentUser;
 */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository userRepository;

    public User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Authentication required"));
    }

    public Long id() {
        return get().getId();
    }
}
