package com.clearing.netting.domain.model;

import java.math.BigDecimal;

/**
 * One converted net position: original currency/netAmount, the FX rate applied
 * (1 for same-currency lines), and the resulting amount in the target currency.
 */
public record ConvertedNetLine(
        String memberId,
        String currency,
        BigDecimal netAmount,
        BigDecimal rate,
        BigDecimal convertedAmount) {
}
