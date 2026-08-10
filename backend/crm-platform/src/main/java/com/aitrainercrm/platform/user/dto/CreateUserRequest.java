package com.aitrainercrm.platform.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/**
 * Admin-initiated "add a teammate" - deliberately has no password field.
 * UserService#invite generates an unusable random password and emails the
 * invitee a set-password link (see EmailService#sendInvitationEmail),
 * because an admin choosing (and therefore knowing) a new teammate's
 * password is a credential-hygiene problem this platform doesn't want to
 * create.
 */
public record CreateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,

        /** Null/empty defaults to the organization's MEMBER role. */
        Set<UUID> roleIds) {
}
