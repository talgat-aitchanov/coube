package com.coube.delivery.model;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record PriceBreakdown(
        BigDecimal basePrice,
        BigDecimal urgentSurcharge,
        BigDecimal cargoTypeSurcharge,
        BigDecimal totalPrice,
        Currency currency
) {
}
