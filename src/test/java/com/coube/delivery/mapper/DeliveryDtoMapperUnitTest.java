package com.coube.delivery.mapper;

import com.coube.delivery.controller.dto.CalculateRequest;
import com.coube.delivery.controller.dto.CalculateResponse;
import com.coube.delivery.model.CargoType;
import com.coube.delivery.model.Currency;
import com.coube.delivery.model.DeliveryInput;
import com.coube.delivery.model.PriceBreakdown;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

import static org.assertj.core.api.BDDAssertions.then;

class DeliveryDtoMapperUnitTest {

    private final DeliveryDtoMapper mapper = Mappers.getMapper(DeliveryDtoMapper.class);

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    @DisplayName("Maps distanceKm, weightTon, cargoType, and isUrgent from request to domain input")
    void toInput_mapsAllFieldsFromRequestToDomainModel() {
        // given
        CalculateRequest request = new CalculateRequest(bd("450"), bd("12.5"), CargoType.FRAGILE, true);

        // when
        DeliveryInput input = mapper.toInput(request);

        // then
        then(input.distanceKm()).isEqualByComparingTo("450");
        then(input.weightTon()).isEqualByComparingTo("12.5");
        then(input.cargoType()).isEqualTo(CargoType.FRAGILE);
        then(input.urgent()).isTrue();
    }

    @Test
    @DisplayName("Maps isUrgent=false from request to urgent=false in domain input")
    void toInput_mapsNonUrgentFlagCorrectly() {
        // given
        CalculateRequest request = new CalculateRequest(bd("100"), bd("5"), CargoType.STANDARD, false);

        // when
        DeliveryInput input = mapper.toInput(request);

        // then
        then(input.urgent()).isFalse();
    }

    @Test
    @DisplayName("Maps OVERSIZED cargo type from request to domain input unchanged")
    void toInput_mapsOversizedCargoType() {
        // given
        CalculateRequest request = new CalculateRequest(bd("200"), bd("20"), CargoType.OVERSIZED, false);

        // when
        DeliveryInput input = mapper.toInput(request);

        // then
        then(input.cargoType()).isEqualTo(CargoType.OVERSIZED);
    }

    @Test
    @DisplayName("Maps all price fields and currency from PriceBreakdown to CalculateResponse")
    void toResponse_mapsAllBreakdownFieldsIncludingCurrency() {
        // given
        PriceBreakdown breakdown = PriceBreakdown.builder()
                .basePrice(bd("45000.00"))
                .urgentSurcharge(bd("9000.00"))
                .cargoTypeSurcharge(bd("4500.00"))
                .totalPrice(bd("58500.00"))
                .currency(Currency.KZT)
                .build();

        // when
        CalculateResponse response = mapper.toResponse(breakdown);

        // then
        then(response.basePrice()).isEqualByComparingTo("45000.00");
        then(response.urgentSurcharge()).isEqualByComparingTo("9000.00");
        then(response.cargoTypeSurcharge()).isEqualByComparingTo("4500.00");
        then(response.totalPrice()).isEqualByComparingTo("58500.00");
        then(response.currency()).isEqualTo(Currency.KZT);
    }

    @Test
    @DisplayName("Maps zero surcharges for non-urgent STANDARD cargo so totalPrice equals basePrice")
    void toResponse_mapsZeroSurcharges_whenStandardNonUrgent() {
        // given
        PriceBreakdown breakdown = PriceBreakdown.builder()
                .basePrice(bd("4000.00"))
                .urgentSurcharge(bd("0.00"))
                .cargoTypeSurcharge(bd("0.00"))
                .totalPrice(bd("4000.00"))
                .currency(Currency.KZT)
                .build();

        // when
        CalculateResponse response = mapper.toResponse(breakdown);

        // then
        then(response.urgentSurcharge()).isEqualByComparingTo("0.00");
        then(response.cargoTypeSurcharge()).isEqualByComparingTo("0.00");
        then(response.totalPrice()).isEqualByComparingTo(response.basePrice());
    }

    @Test
    @DisplayName("Maps minimum boundary values without rounding loss")
    void toResponse_mapsMinimumBoundaryValues() {
        // given
        PriceBreakdown breakdown = PriceBreakdown.builder()
                .basePrice(bd("0.80"))
                .urgentSurcharge(bd("0.00"))
                .cargoTypeSurcharge(bd("0.00"))
                .totalPrice(bd("0.80"))
                .currency(Currency.KZT)
                .build();

        // when
        CalculateResponse response = mapper.toResponse(breakdown);

        // then
        then(response.basePrice()).isEqualByComparingTo("0.80");
        then(response.totalPrice()).isEqualByComparingTo("0.80");
    }
}
