package com.aitrainercrm.platform.auth.exception;

import com.aitrainercrm.platform.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Deliberately generic message: never tell a caller which half (email vs
 * password) was wrong, or whether the account even exists - that's how
 * login endpoints leak valid usernames to an attacker doing enumeration.
 */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED);
    }
}
