package com.coube.delivery.entity;

import com.coube.delivery.model.CargoType;
import com.coube.delivery.model.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "delivery_calculation")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DeliveryCalculationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "tariff_id")
    private Long tariffId;

    @Column(name = "distance_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "weight_ton", nullable = false, precision = 8, scale = 2)
    private BigDecimal weightTon;

    @Enumerated(EnumType.STRING)
    @Column(name = "cargo_type", nullable = false, length = 20)
    private CargoType cargoType;

    @Column(name = "is_urgent", nullable = false)
    private boolean urgent;

    @Column(name = "base_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "urgent_surcharge", nullable = false, precision = 14, scale = 2)
    private BigDecimal urgentSurcharge;

    @Column(name = "cargo_type_surcharge", nullable = false, precision = 14, scale = 2)
    private BigDecimal cargoTypeSurcharge;

    @Column(name = "total_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Generated(GenerationTime.INSERT)
    @Column(name = "calculated_at", nullable = false, insertable = false, updatable = false)
    private Instant calculatedAt;
}
