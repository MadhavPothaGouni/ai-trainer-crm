package com.aitrainercrm.platform.loyalty.dto;

import java.util.UUID;

/** The live sum of a contact's non-deleted loyalty transactions - see {@code LoyaltyTransactionService#getBalance}. */
public record LoyaltyBalanceDto(UUID contactId, long balance) {
}
