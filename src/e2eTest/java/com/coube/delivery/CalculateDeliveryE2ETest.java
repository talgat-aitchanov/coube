package com.coube.delivery;

import com.coube.delivery.repository.DeliveryCalculationRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.BDDAssertions.then;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CalculateDeliveryE2ETest {

    private static final String CALCULATE_URL = "/api/v1/delivery/calculate";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    DeliveryCalculationRepository calculationRepository;

    @Test
    @DisplayName("FRAGILE urgent request returns correct breakdown and persists one row to the database")
    void calculate_returnsCorrectBreakdownAndPersistsRow_forFragileUrgentRequest() {
        // given
        long countBefore = calculationRepository.count();
        String requestBody = """
                {"distanceKm": 450, "weightTon": 12.5, "cargoType": "FRAGILE", "isUrgent": true}
                """;

        // when
        RestAssured.given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post(CALCULATE_URL)
                .then()
                .statusCode(200)
                .body("basePrice", equalTo(45000.00f))
                .body("urgentSurcharge", equalTo(9000.00f))
                .body("cargoTypeSurcharge", equalTo(4500.00f))
                .body("totalPrice", equalTo(58500.00f))
                .body("currency", equalTo("KZT"));

        // then — audit row persisted
        then(calculationRepository.count()).isEqualTo(countBefore + 1);
    }

    @Test
    @DisplayName("STANDARD non-urgent request returns zero surcharges and totalPrice equals basePrice")
    void calculate_returnsZeroSurchargesAndTotalEqualsBase_forStandardNonUrgentRequest() {
        // given
        String requestBody = """
                {"distanceKm": 100, "weightTon": 5, "cargoType": "STANDARD", "isUrgent": false}
                """;

        // when / then
        RestAssured.given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post(CALCULATE_URL)
                .then()
                .statusCode(200)
                .body("basePrice", equalTo(4000.00f))
                .body("urgentSurcharge", equalTo(0.00f))
                .body("cargoTypeSurcharge", equalTo(0.00f))
                .body("totalPrice", equalTo(4000.00f))
                .body("currency", equalTo("KZT"));
    }

    @Test
    @DisplayName("OVERSIZED urgent request returns combined cargo and urgency surcharges")
    void calculate_returnsCombinedCargoAndUrgencySurcharges_forOversizedUrgentRequest() {
        // given
        String requestBody = """
                {"distanceKm": 200, "weightTon": 10, "cargoType": "OVERSIZED", "isUrgent": true}
                """;

        // when / then
        RestAssured.given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post(CALCULATE_URL)
                .then()
                .statusCode(200)
                .body("basePrice", equalTo(16000.00f))
                .body("urgentSurcharge", equalTo(3200.00f))
                .body("cargoTypeSurcharge", equalTo(4000.00f))
                .body("totalPrice", equalTo(23200.00f));
    }

    @Test
    @DisplayName("Request at minimum boundary (1 km, 0.1 t) returns 200 with small but valid prices")
    void calculate_returns200WithSmallPrices_atMinimumDistanceAndWeightBoundary() {
        // given
        String requestBody = """
                {"distanceKm": 1, "weightTon": 0.1, "cargoType": "STANDARD", "isUrgent": false}
                """;

        // when / then
        RestAssured.given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post(CALCULATE_URL)
                .then()
                .statusCode(200)
                .body("basePrice", equalTo(0.80f))
                .body("totalPrice", equalTo(0.80f));
    }

    @Test
    @DisplayName("Returns 400 with title 'Validation Error' when distanceKm is 0")
    void calculate_returns400WithValidationErrorTitle_whenDistanceKmIsZero() {
        // given
        String requestBody = """
                {"distanceKm": 0, "weightTon": 12.5, "cargoType": "FRAGILE", "isUrgent": true}
                """;

        // when / then
        RestAssured.given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post(CALCULATE_URL)
                .then()
                .statusCode(400)
                .body("title", equalTo("Validation Error"));
    }

    @Test
    @DisplayName("Response includes X-Correlation-Id header echoed back from the request")
    void calculate_echoesCorrelationIdHeader_whenProvidedInRequest() {
        // given
        String requestBody = """
                {"distanceKm": 100, "weightTon": 5, "cargoType": "STANDARD", "isUrgent": false}
                """;

        // when / then
        RestAssured.given()
                .port(port)
                .contentType(ContentType.JSON)
                .header("X-Correlation-Id", "e2e-test-corr-456")
                .body(requestBody)
                .when()
                .post(CALCULATE_URL)
                .then()
                .statusCode(200)
                .header("X-Correlation-Id", "e2e-test-corr-456");
    }

    @Test
    @DisplayName("Actuator health endpoint reports status UP")
    void actuatorHealth_reportsStatusUp() {
        // when / then
        RestAssured.given()
                .port(port)
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("Actuator metrics endpoint exposes delivery.calculations.total after a calculation")
    void actuatorMetrics_exposesDeliveryCalculationsTotalMetric_afterCalculation() {
        // given — trigger a calculation so the counter is registered
        RestAssured.given()
                .port(port)
                .contentType(ContentType.JSON)
                .body("""
                        {"distanceKm": 100, "weightTon": 5, "cargoType": "STANDARD", "isUrgent": false}
                        """)
                .when()
                .post(CALCULATE_URL)
                .then()
                .statusCode(200);

        // when / then
        RestAssured.given()
                .port(port)
                .when()
                .get("/actuator/metrics/delivery.calculations.total")
                .then()
                .statusCode(200)
                .body("name", equalTo("delivery.calculations.total"));
    }
}
