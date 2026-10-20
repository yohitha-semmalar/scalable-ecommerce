# Multi-stage Dockerfile for Spring Boot Web Crawler & Price Comparison Microservice
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B || true

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/web_crawler-0.0.1-SNAPSHOT.jar app.jar

ENV PORT=8081 \
    SPRING_DATA_REDIS_HOST=redis \
    SPRING_DATA_REDIS_PORT=6379 \
    AMAZON_MOCK_URL=http://mock-amazon:8085 \
    FLIPKART_MOCK_URL=http://mock-flipkart:8086 \
    MEESHO_MOCK_URL=http://mock-meesho:8087 \
    INSTAMART_MOCK_URL=http://mock-instamart:8088

EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
