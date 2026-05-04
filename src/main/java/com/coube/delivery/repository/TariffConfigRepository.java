package com.coube.delivery.repository;

import com.coube.delivery.entity.TariffConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TariffConfigRepository extends JpaRepository<TariffConfigEntity, Long> {

    // DB unique partial index (one_active_tariff_per_currency) guarantees at most one active row,
    // so no DISTINCT is needed here.
    @Query("SELECT t FROM TariffConfigEntity t LEFT JOIN FETCH t.surcharges WHERE t.effectiveTo IS NULL")
    Optional<TariffConfigEntity> findActiveTariff();
}
