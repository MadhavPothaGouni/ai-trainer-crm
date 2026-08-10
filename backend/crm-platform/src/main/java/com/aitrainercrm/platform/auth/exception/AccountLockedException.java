package com.aitrainercrm.platform.auth.exception;

import com.aitrainercrm.platform.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AccountLockedException extends BusinessException {

    public AccountLockedException(long minutesRemaining) {
        super(
                "ACCOUNT_LOCKED",
                "Too many failed login attempts. Try again in %d minute(s).".formatted(minutesRemaining),
                HttpStatus.LOCKED);
    }
}
