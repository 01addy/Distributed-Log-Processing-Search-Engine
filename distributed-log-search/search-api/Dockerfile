FROM maven:3.9.5-eclipse-temurin-17 AS builder

WORKDIR /app

COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY ingestion/pom.xml ingestion/pom.xml
COPY processor/pom.xml processor/pom.xml
COPY search-api/pom.xml search-api/pom.xml

RUN mvn dependency:go-offline -pl common,search-api -am -q

COPY common/src common/src
COPY search-api/src search-api/src

RUN mvn clean package -pl common,search-api -am -DskipTests -q

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser

COPY --from=builder /app/search-api/target/search-api-*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "app.jar"]
