package com.coube.delivery.entity;

import com.coube.delivery.model.CargoType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
        name = "cargo_surcharge",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tariff_id", "cargo_type"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CargoSurchargeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tariff_id", nullable = false)
    private TariffConfigEntity tariff;

    @Enumerated(EnumType.STRING)
    @Column(name = "cargo_type", nullable = false, length = 20)
    private CargoType cargoType;

    @Column(name = "surcharge_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal surchargeRate;

    @Column(name = "effective_from", nullable = false, insertable = false, updatable = false)
    @Generated(GenerationTime.INSERT)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Generated(GenerationTime.INSERT)
    private Instant createdAt;
}
