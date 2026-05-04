package com.coube.delivery.mapper;

import com.coube.delivery.controller.dto.CalculateRequest;
import com.coube.delivery.controller.dto.CalculateResponse;
import com.coube.delivery.model.DeliveryInput;
import com.coube.delivery.model.PriceBreakdown;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DeliveryDtoMapper {

    @Mapping(target = "urgent", source = "isUrgent")
    DeliveryInput toInput(CalculateRequest request);

    CalculateResponse toResponse(PriceBreakdown breakdown);
}
