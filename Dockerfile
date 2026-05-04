# Multi-stage build for the Coube delivery API.
# Stage 1: build the JAR with the Gradle wrapper.
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --version
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: minimal runtime image.
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S coube && adduser -S coube -G coube
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
USER coube
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
