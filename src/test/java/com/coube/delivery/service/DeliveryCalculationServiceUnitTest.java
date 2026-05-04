package com.coube.delivery.service;

import com.coube.delivery.entity.CargoSurchargeEntity;
import com.coube.delivery.entity.DeliveryCalculationEntity;
import com.coube.delivery.entity.TariffConfigEntity;
import com.coube.delivery.exception.TariffNotFoundException;
import com.coube.delivery.mapper.DeliveryEntityMapper;
import com.coube.delivery.model.CargoType;
import com.coube.delivery.model.Currency;
import com.coube.delivery.model.DeliveryInput;
import com.coube.delivery.model.PriceBreakdown;
import com.coube.delivery.repository.DeliveryCalculationRepository;
import com.coube.delivery.repository.TariffConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

class DeliveryCalculationServiceUnitTest {

    private static final Long TARIFF_ID = 1L;

    private TariffConfigRepository tariffRepository;
    private DeliveryCalculationRepository calculationRepository;
    private DeliveryCalculationService service;

    static Stream<Arguments> priceBreakdownCases() {
        // distanceKm, weightTon, cargoType, urgent, expectedBase, expectedUrgent, expectedCargo, expectedTotal
        return Stream.of(
                // FRAGILE, urgent — 10% cargo surcharge + 20% urgency premium
                Arguments.of(bd("450"), bd("12.5"), CargoType.FRAGILE, true, bd("45000.00"), bd("9000.00"), bd("4500.00"), bd("58500.00")),
                // STANDARD, non-urgent — zero surcharges
                Arguments.of(bd("100"), bd("5"), CargoType.STANDARD, false, bd("4000.00"), bd("0.00"), bd("0.00"), bd("4000.00")),
                // STANDARD, urgent — urgency premium only, no cargo surcharge
                Arguments.of(bd("100"), bd("5"), CargoType.STANDARD, true, bd("4000.00"), bd("800.00"), bd("0.00"), bd("4800.00")),
                // OVERSIZED, non-urgent — 25% cargo surcharge only
                Arguments.of(bd("200"), bd("10"), CargoType.OVERSIZED, false, bd("16000.00"), bd("0.00"), bd("4000.00"), bd("20000.00")),
                // OVERSIZED, urgent — 25% cargo surcharge + 20% urgency premium
                Arguments.of(bd("200"), bd("10"), CargoType.OVERSIZED, true, bd("16000.00"), bd("3200.00"), bd("4000.00"), bd("23200.00")),
                // FRAGILE, non-urgent — cargo surcharge only, no urgency premium
                Arguments.of(bd("100"), bd("10"), CargoType.FRAGILE, false, bd("8000.00"), bd("0.00"), bd("800.00"), bd("8800.00")),
                // Minimum boundary: 1 km x 0.1 t x STANDARD non-urgent
                Arguments.of(bd("1"), bd("0.1"), CargoType.STANDARD, false, bd("0.80"), bd("0.00"), bd("0.00"), bd("0.80")),
                // Minimum boundary: 1 km x 0.1 t x OVERSIZED urgent
                Arguments.of(bd("1"), bd("0.1"), CargoType.OVERSIZED, true, bd("0.80"), bd("0.16"), bd("0.20"), bd("1.16")),
                // Maximum boundary: 5000 km x 120 t x OVERSIZED urgent
                Arguments.of(bd("5000"), bd("120"), CargoType.OVERSIZED, true, bd("4800000.00"), bd("960000.00"), bd("1200000.00"), bd("6960000.00"))
        );
    }

    private static TariffConfigEntity seededTariff() {
        TariffConfigEntity tariff = TariffConfigEntity.builder()
                .id(TARIFF_ID)
                .baseRate(bd("8.0000"))
                .urgentRate(bd("0.2000"))
                .effectiveFrom(Instant.parse("2026-01-01T00:00:00Z"))
                .surcharges(new ArrayList<>())
                .build();
        tariff.getSurcharges().addAll(List.of(
                CargoSurchargeEntity.builder().tariff(tariff).cargoType(CargoType.STANDARD).surchargeRate(bd("0.0000")).build(),
                CargoSurchargeEntity.builder().tariff(tariff).cargoType(CargoType.FRAGILE).surchargeRate(bd("0.1000")).build(),
                CargoSurchargeEntity.builder().tariff(tariff).cargoType(CargoType.OVERSIZED).surchargeRate(bd("0.2500")).build()
        ));
        return tariff;
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @BeforeEach
    void setUp() {
        tariffRepository = mock(TariffConfigRepository.class);
        calculationRepository = mock(DeliveryCalculationRepository.class);
        DeliveryEntityMapper entityMapper = new DeliveryEntityMapper() {};
        service = new DeliveryCalculationService(tariffRepository, calculationRepository, entityMapper);

        given(tariffRepository.findActiveTariff()).willReturn(Optional.of(seededTariff()));
        given(calculationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    @ParameterizedTest(name = "{2} urgent={3}: {0}km x {1}t -> total {7}")
    @MethodSource("priceBreakdownCases")
    @DisplayName("Returns correct price breakdown for all cargo type, urgency, and boundary combinations")
    void calculate_returnsCorrectPriceBreakdown_forAllCargoUrgencyAndBoundaryCombinations(
            BigDecimal distanceKm,
            BigDecimal weightTon,
            CargoType cargoType,
            boolean urgent,
            BigDecimal expectedBase,
            BigDecimal expectedUrgent,
            BigDecimal expectedCargo,
            BigDecimal expectedTotal
    ) {
        // given
        DeliveryInput input = DeliveryInput.builder()
                .distanceKm(distanceKm)
                .weightTon(weightTon)
                .cargoType(cargoType)
                .urgent(urgent)
                .build();

        // when
        PriceBreakdown breakdown = service.calculate(input);

        // then
        then(breakdown.basePrice()).isEqualByComparingTo(expectedBase);
        then(breakdown.urgentSurcharge()).isEqualByComparingTo(expectedUrgent);
        then(breakdown.cargoTypeSurcharge()).isEqualByComparingTo(expectedCargo);
        then(breakdown.totalPrice()).isEqualByComparingTo(expectedTotal);
        then(breakdown.currency()).isEqualTo(Currency.KZT);
    }

    @Test
    @DisplayName("Persisted audit row carries tariff id, all input fields, and computed prices; calculated_at is left for the DB")
    void calculate_persistsAuditRowWithTariffIdInputFieldsAndComputedPrices() {
        // given
        AtomicReference<DeliveryCalculationEntity> captured = new AtomicReference<>();
        given(calculationRepository.save(any(DeliveryCalculationEntity.class)))
                .willAnswer(inv -> {
                    captured.set(inv.getArgument(0));
                    return inv.getArgument(0);
                });
        DeliveryInput input = DeliveryInput.builder()
                .distanceKm(bd("450"))
                .weightTon(bd("12.5"))
                .cargoType(CargoType.FRAGILE)
                .urgent(true)
                .build();

        // when
        service.calculate(input);

        // then
        DeliveryCalculationEntity saved = captured.get();
        then(saved).isNotNull();
        then(saved.getTariffId()).isEqualTo(TARIFF_ID);
        then(saved.getDistanceKm()).isEqualByComparingTo("450");
        then(saved.getWeightTon()).isEqualByComparingTo("12.5");
        then(saved.getCargoType()).isEqualTo(CargoType.FRAGILE);
        then(saved.isUrgent()).isTrue();
        then(saved.getBasePrice()).isEqualByComparingTo("45000.00");
        then(saved.getUrgentSurcharge()).isEqualByComparingTo("9000.00");
        then(saved.getCargoTypeSurcharge()).isEqualByComparingTo("4500.00");
        then(saved.getTotalPrice()).isEqualByComparingTo("58500.00");
        then(saved.getCurrency()).isEqualTo(Currency.KZT);
        // calculated_at is DB-generated (DEFAULT NOW()); mapper intentionally leaves it null
        then(saved.getCalculatedAt()).isNull();
    }

    @Test
    @DisplayName("Throws TariffNotFoundException when no active tariff is configured in the database")
    void calculate_throwsTariffNotFoundException_whenNoActiveTariffExists() {
        // given
        given(tariffRepository.findActiveTariff()).willReturn(Optional.empty());
        DeliveryInput input = DeliveryInput.builder()
                .distanceKm(bd("100"))
                .weightTon(bd("1"))
                .cargoType(CargoType.STANDARD)
                .urgent(false)
                .build();

        // when / then
        thenThrownBy(() -> service.calculate(input))
                .isInstanceOf(TariffNotFoundException.class)
                .hasMessageContaining("active tariff");
    }
}
