FROM maven:3.9.5-eclipse-temurin-17 AS builder

WORKDIR /app

COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY ingestion/pom.xml ingestion/pom.xml
COPY processor/pom.xml processor/pom.xml
COPY search-api/pom.xml search-api/pom.xml

RUN mvn dependency:go-offline -pl common,processor -am -q

COPY common/src common/src
COPY processor/src processor/src

RUN mvn clean package -pl common,processor -am -DskipTests -q

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser

COPY --from=builder /app/processor/target/processor-*.jar app.jar

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "app.jar"]
