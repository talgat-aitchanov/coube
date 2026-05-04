package com.coube.delivery.model;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record DeliveryInput(
        BigDecimal distanceKm,
        BigDecimal weightTon,
        CargoType cargoType,
        boolean urgent
) {
}
