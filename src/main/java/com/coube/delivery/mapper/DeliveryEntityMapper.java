package com.coube.delivery.mapper;

import com.coube.delivery.entity.CargoSurchargeEntity;
import com.coube.delivery.entity.DeliveryCalculationEntity;
import com.coube.delivery.entity.TariffConfigEntity;
import com.coube.delivery.model.CargoType;
import com.coube.delivery.model.DeliveryInput;
import com.coube.delivery.model.PriceBreakdown;
import com.coube.delivery.model.TariffRates;
import org.mapstruct.Mapper;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface DeliveryEntityMapper {

    default TariffRates toTariffRates(TariffConfigEntity entity) {
        Map<CargoType, BigDecimal> cargoRates = new EnumMap<>(CargoType.class);
        for (CargoSurchargeEntity surcharge : entity.getSurcharges()) {
            cargoRates.put(surcharge.getCargoType(), surcharge.getSurchargeRate());
        }
        return TariffRates.builder()
                .id(entity.getId())
                .baseRate(entity.getBaseRate())
                .urgentRate(entity.getUrgentRate())
                .cargoRates(cargoRates)
                .build();
    }

    default DeliveryCalculationEntity toEntity(DeliveryInput input, PriceBreakdown breakdown, Long tariffId) {
        return DeliveryCalculationEntity.builder()
                .tariffId(tariffId)
                .distanceKm(input.distanceKm())
                .weightTon(input.weightTon())
                .cargoType(input.cargoType())
                .urgent(input.urgent())
                .basePrice(breakdown.basePrice())
                .urgentSurcharge(breakdown.urgentSurcharge())
                .cargoTypeSurcharge(breakdown.cargoTypeSurcharge())
                .totalPrice(breakdown.totalPrice())
                .currency(breakdown.currency())
                .build();
    }
}
