# Multi-stage build for lean production image
FROM maven:3.9.5-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy parent POM and module POMs first (layer caching for dependencies)
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY ingestion/pom.xml ingestion/pom.xml
COPY processor/pom.xml processor/pom.xml
COPY search-api/pom.xml search-api/pom.xml

# Download dependencies (cached layer unless pom.xml changes)
RUN mvn dependency:go-offline -pl common,ingestion -am -q

# Copy source and build
COPY common/src common/src
COPY ingestion/src ingestion/src

RUN mvn clean package -pl common,ingestion -am -DskipTests -q

# ── Runtime stage ──────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Non-root user for security
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser

COPY --from=builder /app/ingestion/target/ingestion-*.jar app.jar

EXPOSE 8081

# JVM tuning for containers
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "app.jar"]
