package com.coube.delivery.service;

import com.coube.delivery.entity.DeliveryCalculationEntity;
import com.coube.delivery.entity.TariffConfigEntity;
import com.coube.delivery.exception.TariffNotFoundException;
import com.coube.delivery.mapper.DeliveryEntityMapper;
import com.coube.delivery.model.CargoType;
import com.coube.delivery.model.Currency;
import com.coube.delivery.model.DeliveryInput;
import com.coube.delivery.model.PriceBreakdown;
import com.coube.delivery.model.TariffRates;
import com.coube.delivery.repository.DeliveryCalculationRepository;
import com.coube.delivery.repository.TariffConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryCalculationService {

    private static final int MONEY_SCALE = 2;

    private final TariffConfigRepository tariffConfigRepository;
    private final DeliveryCalculationRepository calculationRepository;
    private final DeliveryEntityMapper entityMapper;

    @Transactional
    public PriceBreakdown calculate(DeliveryInput input) {
        log.info("Calculating delivery: distanceKm={}, weightTon={}, cargoType={}, urgent={}",
                input.distanceKm(), input.weightTon(), input.cargoType(), input.urgent());

        TariffConfigEntity tariffEntity = tariffConfigRepository.findActiveTariff()
                .orElseThrow(() -> new TariffNotFoundException("No active tariff is configured"));

        TariffRates rates = entityMapper.toTariffRates(tariffEntity);
        PriceBreakdown breakdown = computeBreakdown(input, rates);

        DeliveryCalculationEntity entity = entityMapper.toEntity(input, breakdown, rates.id());
        calculationRepository.save(entity);
        log.info("Delivery calculation persisted: totalPrice={} {}, tariffId={}",
                breakdown.totalPrice(), breakdown.currency(), rates.id());

        return breakdown;
    }

    private PriceBreakdown computeBreakdown(DeliveryInput input, TariffRates rates) {
        BigDecimal basePrice = input.distanceKm()
                .multiply(input.weightTon())
                .multiply(rates.baseRate())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal urgentSurcharge = input.urgent()
                ? basePrice.multiply(rates.urgentRate()).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(MONEY_SCALE);

        BigDecimal cargoTypeSurcharge = basePrice
                .multiply(cargoRate(rates, input.cargoType()))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalPrice = basePrice.add(urgentSurcharge).add(cargoTypeSurcharge);

        return PriceBreakdown.builder()
                .basePrice(basePrice)
                .urgentSurcharge(urgentSurcharge)
                .cargoTypeSurcharge(cargoTypeSurcharge)
                .totalPrice(totalPrice)
                .currency(Currency.KZT)
                .build();
    }

    private BigDecimal cargoRate(TariffRates rates, CargoType type) {
        return Optional.ofNullable(rates.cargoRates().get(type))
                .orElseThrow(() -> new TariffNotFoundException("No surcharge rate configured for cargo type " + type));
    }
}
