package com.aitrainercrm.platform.security.userdetails;

import com.aitrainercrm.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges our User entity to Spring Security's authentication machinery.
 * Used both by the login flow (AuthenticationManager calls this to check a
 * submitted password) and, indirectly, nowhere else - authenticated
 * requests are validated against the JWT itself (see JwtAuthenticationFilter),
 * not by hitting the database on every call.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        return userRepository
                .findByEmailAndDeletedAtIsNull(normalizedEmail)
                .map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + normalizedEmail));
    }
}
