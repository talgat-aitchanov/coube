package com.coube.delivery.model;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;

@Builder
public record TariffRates(
        Long id,
        BigDecimal baseRate,
        BigDecimal urgentRate,
        Map<CargoType, BigDecimal> cargoRates
) {
}
