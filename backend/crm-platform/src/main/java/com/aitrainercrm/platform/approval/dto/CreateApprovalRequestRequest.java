package com.aitrainercrm.platform.approval.dto;

import com.aitrainercrm.platform.approval.entity.ApprovalRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** approverUserIds is ordered - index 0 is step 1, and so on; the same user id twice is rejected by ApprovalRequestService (see its javadoc) since "approve your own earlier step again" isn't a meaningful second sign-off. */
public record CreateApprovalRequestRequest(
        @NotNull ApprovalRequest.RelatedToType relatedToType,
        @NotNull UUID relatedToId,
        @NotBlank @Size(max = 300) String title,
        @NotEmpty List<UUID> approverUserIds) {
}
