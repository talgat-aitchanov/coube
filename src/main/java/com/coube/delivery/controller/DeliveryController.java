package com.coube.delivery.controller;

import com.coube.delivery.controller.dto.CalculateRequest;
import com.coube.delivery.controller.dto.CalculateResponse;
import com.coube.delivery.mapper.DeliveryDtoMapper;
import com.coube.delivery.model.DeliveryInput;
import com.coube.delivery.model.PriceBreakdown;
import com.coube.delivery.service.DeliveryCalculationService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
@Tag(name = "Delivery", description = "Delivery price calculation")
public class DeliveryController {

    private final DeliveryCalculationService service;
    private final DeliveryDtoMapper dtoMapper;

    @PostMapping(
            path = "/calculate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Timed(
            value = "delivery.calculations.total",
            description = "Delivery price calculations — timing, count, and error tracking per class/method/exception"
    )
    @Operation(
            summary = "Calculate delivery price",
            description = "Computes the delivery price from distance, weight, cargo type, and urgency. "
                    + "The result is persisted as an audit record."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Price breakdown returned successfully",
                    content = @Content(schema = @Schema(implementation = CalculateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input — field validation failed",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "No active tariff is configured in the database",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public CalculateResponse calculate(@Valid @RequestBody CalculateRequest request) {
        DeliveryInput input = dtoMapper.toInput(request);
        PriceBreakdown breakdown = service.calculate(input);
        return dtoMapper.toResponse(breakdown);
    }
}
