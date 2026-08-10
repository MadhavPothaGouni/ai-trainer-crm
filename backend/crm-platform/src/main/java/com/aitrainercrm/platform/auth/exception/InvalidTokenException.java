package com.aitrainercrm.platform.auth.exception;

import com.aitrainercrm.platform.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Covers expired, revoked, reused, and malformed refresh/reset/verification tokens alike. */
public class InvalidTokenException extends BusinessException {

    public InvalidTokenException(String message) {
        super("INVALID_TOKEN", message, HttpStatus.UNAUTHORIZED);
    }
}
