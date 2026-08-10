package com.aitrainercrm.platform.common.exception;

import org.springframework.http.HttpStatus;

/** Authenticated, but the RBAC scope check (own/team/department/org) failed. */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }
}
