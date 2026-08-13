package com.aitrainercrm.platform.gdpr.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Shared request body for both POST /data-subject-requests/export and .../erase - the only input either action needs is the subject's email address. */
public record CreateDataSubjectRequest(@NotBlank @Email String subjectEmail) {
}
