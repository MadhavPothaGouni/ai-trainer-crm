package com.aitrainercrm.platform.lead.dto;

import com.aitrainercrm.platform.lead.entity.Lead;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateLeadRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        @Size(max = 200) String companyName,
        @Size(max = 150) String title,
        @NotNull Lead.Source source,
        @Size(max = 2000) String description,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
