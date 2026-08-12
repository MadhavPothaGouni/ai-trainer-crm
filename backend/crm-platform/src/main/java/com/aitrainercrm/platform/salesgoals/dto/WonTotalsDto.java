package com.aitrainercrm.platform.salesgoals.dto;

import java.math.BigDecimal;

/** One goal's actuals for its period: how many opportunities closed CLOSED_WON, and their summed amount - SalesGoalService picks whichever field matches the goal's Metric. */
public record WonTotalsDto(long dealCount, BigDecimal totalValue) {
}
