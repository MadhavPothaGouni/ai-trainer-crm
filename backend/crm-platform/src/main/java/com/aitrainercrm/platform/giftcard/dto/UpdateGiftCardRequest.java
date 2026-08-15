package com.aitrainercrm.platform.giftcard.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Balance is deliberately not editable here - see CreateGiftCardRequest for the initial balance and GiftCardController's POST .../redeem for the only way it moves afterward. Status is likewise separate - see UpdateGiftCardStatusRequest. */
public record UpdateGiftCardRequest(LocalDate expiresAt, @Size(max = 2000) String notes) {
}
