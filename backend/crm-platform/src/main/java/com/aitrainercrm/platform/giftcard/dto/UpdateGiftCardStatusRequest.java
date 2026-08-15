package com.aitrainercrm.platform.giftcard.dto;

import com.aitrainercrm.platform.giftcard.entity.GiftCard;
import jakarta.validation.constraints.NotNull;

public record UpdateGiftCardStatusRequest(@NotNull GiftCard.Status status) {
}
