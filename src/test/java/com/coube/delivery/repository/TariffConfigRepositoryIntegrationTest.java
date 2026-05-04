package com.coube.delivery.repository;

import com.coube.delivery.entity.TariffConfigEntity;
import com.coube.delivery.model.CargoType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.BDDAssertions.then;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TariffConfigRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TariffConfigRepository repository;

    @Test
    @DisplayName("Returns seeded active tariff with base rate 8.0 and urgent rate 0.2")
    void findActiveTariff_returnsSeededTariffWithCorrectRates() {
        // when
        Optional<TariffConfigEntity> active = repository.findActiveTariff();

        // then
        then(active).isPresent();
        TariffConfigEntity tariff = active.get();
        then(tariff.getBaseRate()).isEqualByComparingTo("8.0000");
        then(tariff.getUrgentRate()).isEqualByComparingTo("0.2000");
    }

    @Test
    @DisplayName("Active tariff has effectiveTo=null confirming it is open-ended")
    void findActiveTariff_hasNullEffectiveTo_confirmingItIsActive() {
        // when
        Optional<TariffConfigEntity> active = repository.findActiveTariff();

        // then
        then(active).isPresent();
        then(active.get().getEffectiveTo()).isNull();
    }

    @Test
    @DisplayName("Active tariff has a non-null Long id and effectiveFrom assigned by the database")
    void findActiveTariff_hasNonNullIdAndEffectiveFrom() {
        // when
        Optional<TariffConfigEntity> active = repository.findActiveTariff();

        // then
        then(active).isPresent();
        TariffConfigEntity tariff = active.get();
        then(tariff.getId()).isNotNull().isInstanceOf(Long.class);
        then(tariff.getEffectiveFrom()).isNotNull();
    }

    @Test
    @DisplayName("Active tariff includes surcharge entries for all three cargo types: FRAGILE, OVERSIZED, STANDARD")
    void findActiveTariff_includesSurchargesForAllThreeCargoTypes() {
        // when
        Optional<TariffConfigEntity> active = repository.findActiveTariff();

        // then
        then(active).isPresent();
        then(active.get().getSurcharges())
                .extracting(s -> s.getCargoType())
                .containsExactlyInAnyOrder(CargoType.FRAGILE, CargoType.OVERSIZED, CargoType.STANDARD);
    }

    @Test
    @DisplayName("Seeded cargo surcharge rates are STANDARD=0%, FRAGILE=10%, OVERSIZED=25%")
    void findActiveTariff_hasCorrectedSurchargeRateForEachCargoType() {
        // when
        Optional<TariffConfigEntity> active = repository.findActiveTariff();

        // then
        then(active).isPresent();
        Map<CargoType, BigDecimal> rates = active.get().getSurcharges().stream()
                .collect(Collectors.toMap(s -> s.getCargoType(), s -> s.getSurchargeRate()));

        then(rates.get(CargoType.STANDARD)).isEqualByComparingTo("0.0000");
        then(rates.get(CargoType.FRAGILE)).isEqualByComparingTo("0.1000");
        then(rates.get(CargoType.OVERSIZED)).isEqualByComparingTo("0.2500");
    }
}
