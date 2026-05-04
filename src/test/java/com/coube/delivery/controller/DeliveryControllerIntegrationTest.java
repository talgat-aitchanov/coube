package com.coube.delivery.controller;

import com.coube.delivery.controller.dto.CalculateResponse;
import com.coube.delivery.exception.GlobalExceptionHandler;
import com.coube.delivery.exception.TariffNotFoundException;
import com.coube.delivery.mapper.DeliveryDtoMapper;
import com.coube.delivery.model.Currency;
import com.coube.delivery.model.PriceBreakdown;
import com.coube.delivery.service.DeliveryCalculationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.BDDAssertions.then;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryController.class)
@Import(GlobalExceptionHandler.class)
class DeliveryControllerIntegrationTest {

    private static final String CALCULATE_URL = "/api/v1/delivery/calculate";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    DeliveryCalculationService service;

    @MockitoBean
    DeliveryDtoMapper dtoMapper;

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    @DisplayName("Returns 200 with full price breakdown for a valid FRAGILE urgent request")
    void calculate_returns200WithPriceBreakdown_forValidFragileUrgentRequest() throws Exception {
        // given
        given(dtoMapper.toInput(any())).willReturn(null);
        given(service.calculate(any())).willReturn(PriceBreakdown.builder()
                .basePrice(bd("45000.00")).urgentSurcharge(bd("9000.00"))
                .cargoTypeSurcharge(bd("4500.00")).totalPrice(bd("58500.00")).currency(Currency.KZT).build());
        given(dtoMapper.toResponse(any())).willReturn(CalculateResponse.builder()
                .basePrice(bd("45000.00")).urgentSurcharge(bd("9000.00"))
                .cargoTypeSurcharge(bd("4500.00")).totalPrice(bd("58500.00")).currency(Currency.KZT).build());

        // when
        MvcResult result = mockMvc.perform(post(CALCULATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"distanceKm": 450, "weightTon": 12.5, "cargoType": "FRAGILE", "isUrgent": true}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        // then
        CalculateResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), CalculateResponse.class);
        then(response.basePrice()).isEqualByComparingTo("45000.00");
        then(response.urgentSurcharge()).isEqualByComparingTo("9000.00");
        then(response.cargoTypeSurcharge()).isEqualByComparingTo("4500.00");
        then(response.totalPrice()).isEqualByComparingTo("58500.00");
        then(response.currency()).isEqualTo(Currency.KZT);
    }

    @Test
    @DisplayName("Echoes X-Correlation-Id header back in the response when provided in the request")
    void calculate_echoesCorrelationIdHeader_whenProvidedInRequest() throws Exception {
        // given
        given(dtoMapper.toInput(any())).willReturn(null);
        given(service.calculate(any())).willReturn(PriceBreakdown.builder()
                .basePrice(bd("45000.00")).urgentSurcharge(bd("9000.00"))
                .cargoTypeSurcharge(bd("4500.00")).totalPrice(bd("58500.00")).currency(Currency.KZT).build());
        given(dtoMapper.toResponse(any())).willReturn(CalculateResponse.builder()
                .basePrice(bd("45000.00")).urgentSurcharge(bd("9000.00"))
                .cargoTypeSurcharge(bd("4500.00")).totalPrice(bd("58500.00")).currency(Currency.KZT).build());

        // when / then
        mockMvc.perform(post(CALCULATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "test-corr-123")
                        .content("""
                                {"distanceKm": 450, "weightTon": 12.5, "cargoType": "FRAGILE", "isUrgent": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "test-corr-123"));
    }

    @Test
    @DisplayName("Generates X-Correlation-Id header in the response when not provided in the request")
    void calculate_generatesCorrelationIdHeader_whenNotProvidedInRequest() throws Exception {
        // given
        given(dtoMapper.toInput(any())).willReturn(null);
        given(service.calculate(any())).willReturn(PriceBreakdown.builder()
                .basePrice(bd("4000.00")).urgentSurcharge(bd("0.00"))
                .cargoTypeSurcharge(bd("0.00")).totalPrice(bd("4000.00")).currency(Currency.KZT).build());
        given(dtoMapper.toResponse(any())).willReturn(CalculateResponse.builder()
                .basePrice(bd("4000.00")).urgentSurcharge(bd("0.00"))
                .cargoTypeSurcharge(bd("0.00")).totalPrice(bd("4000.00")).currency(Currency.KZT).build());

        // when / then
        mockMvc.perform(post(CALCULATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"distanceKm": 100, "weightTon": 5, "cargoType": "STANDARD", "isUrgent": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    @DisplayName("Returns 400 with validation error when distanceKm is 0 (below minimum of 1)")
    void calculate_returns400WithValidationError_whenDistanceKmIsZero() throws Exception {
        // given
        String requestBody = """
                {"distanceKm": 0, "weightTon": 12.5, "cargoType": "FRAGILE", "isUrgent": true}
                """;

        // when / then
        mockMvc.perform(post(CALCULATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.errors", containsInAnyOrder("distanceKm must be at least 1")));
    }

    @Test
    @DisplayName("Returns 400 with validation error when distanceKm exceeds maximum of 5000")
    void calculate_returns400WithValidationError_whenDistanceKmExceedsMaximum() throws Exception {
        // given
        String requestBody = """
                {"distanceKm": 5001, "weightTon": 12.5, "cargoType": "FRAGILE", "isUrgent": true}
                """;

        // when / then
        mockMvc.perform(post(CALCULATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", containsInAnyOrder("distanceKm must not exceed 5000")));
    }

    @Test
    @DisplayName("Returns 400 with validation error when weightTon exceeds maximum of 120")
    void calculate_returns400WithValidationError_whenWeightTonExceedsMaximum() throws Exception {
        // given
        String requestBody = """
                {"distanceKm": 100, "weightTon": 200, "cargoType": "STANDARD", "isUrgent": false}
                """;

        // when / then
        mockMvc.perform(post(CALCULATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", containsInAnyOrder("weightTon must not exceed 120")));
    }

    @Test
    @DisplayName("Returns 400 with validation error when weightTon is below minimum of 0.1")
    void calculate_returns400WithValidationError_whenWeightTonIsBelowMinimum() throws Exception {
        // given
        String requestBody = """
                {"distanceKm": 100, "weightTon": 0.09, "cargoType": "STANDARD", "isUrgent": false}
                """;

        // when / then
        mockMvc.perform(post(CALCULATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", containsInAnyOrder("weightTon must be at least 0.1")));
    }

    @Test
    @DisplayName("Returns 400 when cargoType is an unknown enum value")
    void calculate_returns400_whenCargoTypeIsUnknownEnumValue() throws Exception {
        // given
        String requestBody = """
                {"distanceKm": 100, "weightTon": 1, "cargoType": "UNKNOWN", "isUrgent": false}
                """;

        // when / then
        mockMvc.perform(post(CALCULATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("Returns 400 with validation error when isUrgent is missing from the request body")
    void calculate_returns400WithValidationError_whenIsUrgentIsMissing() throws Exception {
        // given
        String requestBody = """
                {"distanceKm": 100, "weightTon": 1, "cargoType": "STANDARD"}
                """;

        // when / then
        mockMvc.perform(post(CALCULATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", containsInAnyOrder("isUrgent must not be null")));
    }

    @Test
    @DisplayName("Returns 400 with multiple validation errors when distanceKm and weightTon are both invalid")
    void calculate_returns400WithMultipleValidationErrors_whenSeveralFieldsAreInvalid() throws Exception {
        // given
        String requestBody = """
                {"distanceKm": 0, "weightTon": 200, "cargoType": "STANDARD", "isUrgent": false}
                """;

        // when / then
        mockMvc.perform(post(CALCULATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors", containsInAnyOrder(
                        "distanceKm must be at least 1",
                        "weightTon must not exceed 120"
                )));
    }

    @Test
    @DisplayName("Returns 503 when no active tariff is configured in the database")
    void calculate_returns503_whenNoActiveTariffIsConfigured() throws Exception {
        // given
        given(dtoMapper.toInput(any())).willReturn(null);
        given(service.calculate(any())).willThrow(new TariffNotFoundException("No active tariff is configured"));

        // when / then
        mockMvc.perform(post(CALCULATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"distanceKm": 100, "weightTon": 1, "cargoType": "STANDARD", "isUrgent": false}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Tariff Not Configured"));
    }
}
