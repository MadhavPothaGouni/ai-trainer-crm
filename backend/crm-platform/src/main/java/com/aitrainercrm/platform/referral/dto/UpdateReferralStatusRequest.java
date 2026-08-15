package com.aitrainercrm.platform.referral.dto;

import com.aitrainercrm.platform.referral.entity.Referral;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * convertedContactId is only meaningful (and only ever applied) when moving to CONVERTED - see
 * ReferralService#updateStatus's javadoc for the stamp-once behavior. It's ignored for every
 * other status, same as how UpdateShiftStatusRequest's status field drives conditional stamping
 * in ShiftService without a separate field per stamped timestamp.
 */
public record UpdateReferralStatusRequest(@NotNull Referral.Status status, UUID convertedContactId) {
}
