package com.coube.delivery.controller.dto;

import com.coube.delivery.model.CargoType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Delivery price calculation request")
public record CalculateRequest(

        @Schema(
                description = "Distance in kilometers. Range: " + DeliveryConstraints.DISTANCE_KM_MIN
                        + "–" + DeliveryConstraints.DISTANCE_KM_MAX,
                minimum = DeliveryConstraints.DISTANCE_KM_MIN,
                maximum = DeliveryConstraints.DISTANCE_KM_MAX,
                example = "450"
        )
        @NotNull
        @DecimalMin(value = DeliveryConstraints.DISTANCE_KM_MIN,
                message = "must be at least " + DeliveryConstraints.DISTANCE_KM_MIN)
        @DecimalMax(value = DeliveryConstraints.DISTANCE_KM_MAX,
                message = "must not exceed " + DeliveryConstraints.DISTANCE_KM_MAX)
        BigDecimal distanceKm,

        @Schema(
                description = "Cargo weight in tons. Range: " + DeliveryConstraints.WEIGHT_TON_MIN
                        + "–" + DeliveryConstraints.WEIGHT_TON_MAX,
                minimum = DeliveryConstraints.WEIGHT_TON_MIN,
                maximum = DeliveryConstraints.WEIGHT_TON_MAX,
                example = "12.5"
        )
        @NotNull
        @DecimalMin(value = DeliveryConstraints.WEIGHT_TON_MIN,
                message = "must be at least " + DeliveryConstraints.WEIGHT_TON_MIN)
        @DecimalMax(value = DeliveryConstraints.WEIGHT_TON_MAX,
                message = "must not exceed " + DeliveryConstraints.WEIGHT_TON_MAX)
        BigDecimal weightTon,

        @Schema(
                description = "Cargo type: STANDARD (no surcharge), FRAGILE (+10%), OVERSIZED (+25%)",
                allowableValues = {"STANDARD", "FRAGILE", "OVERSIZED"},
                example = "FRAGILE"
        )
        @NotNull
        CargoType cargoType,

        @Schema(description = "Whether the shipment requires urgent delivery (+20% surcharge)", example = "true")
        @NotNull
        Boolean isUrgent
) {
}
