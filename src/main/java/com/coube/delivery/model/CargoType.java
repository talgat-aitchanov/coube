package com.coube.delivery.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cargo type classification affecting the surcharge multiplier")
public enum CargoType {
    STANDARD,
    FRAGILE,
    OVERSIZED
}
