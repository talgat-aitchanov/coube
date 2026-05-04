package com.coube.delivery.controller.dto;

import com.coube.delivery.model.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

@Schema(description = "Delivery price breakdown")
@Builder
public record CalculateResponse(

        @Schema(description = "Base price = distanceKm × weightTon × baseRate", example = "45000.00")
        BigDecimal basePrice,

        @Schema(description = "Urgency surcharge (20% of basePrice when urgent, else 0)", example = "9000.00")
        BigDecimal urgentSurcharge,

        @Schema(description = "Cargo type surcharge (0%, 10%, or 25% of basePrice)", example = "4500.00")
        BigDecimal cargoTypeSurcharge,

        @Schema(description = "Total = basePrice + urgentSurcharge + cargoTypeSurcharge", example = "58500.00")
        BigDecimal totalPrice,

        @Schema(description = "Currency of all monetary values", example = "KZT")
        Currency currency
) {
}
