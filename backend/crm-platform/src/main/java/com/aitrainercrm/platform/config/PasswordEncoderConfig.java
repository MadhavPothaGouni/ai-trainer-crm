package com.aitrainercrm.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The {@code PasswordEncoder} bean used to live inside {@link SecurityConfig}
 * itself. It moved out into this standalone, dependency-free configuration
 * class to break a circular bean-creation cycle introduced by the
 * {@code apikey} module: {@code ApiKeyService} needs a {@code PasswordEncoder}
 * to hash key secrets, but {@code SecurityConfig} (which used to own that
 * bean method) also depends on {@code ApiKeyAuthenticationFilter}, which in
 * turn depends on {@code ApiKeyService} - so instantiating {@code SecurityConfig}
 * to get the encoder required {@code ApiKeyService} to already exist, and
 * instantiating {@code ApiKeyService} required the encoder from
 * {@code SecurityConfig}. Spring correctly refuses to resolve that cycle.
 * Splitting this bean into its own configuration class with zero
 * constructor dependencies means creating it never requires creating
 * anything else first, for any future consumer.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Strength 12: noticeably slower than the (default 10) to brute-force
        // offline, still well under 200ms/hash on typical hardware.
        return new BCryptPasswordEncoder(12);
    }
}
