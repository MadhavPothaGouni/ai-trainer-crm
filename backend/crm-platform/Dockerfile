# Multi-stage build: the `maven:*-eclipse-temurin-17` build stage (~500MB+ with the
# full JDK and Maven) never ships - only the resulting jar gets copied into the
# `eclipse-temurin:17-jre-alpine` runtime stage, which stays small and has no build
# tooling at all in the final image.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Dependencies are resolved in their own layer, keyed only on pom.xml, so editing
# application code doesn't invalidate ~150 Spring Boot/Testcontainers/etc. jars on
# every rebuild - only a pom.xml change does.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# Tests already run in CI (backend-ci.yml) against a real Postgres via
# Testcontainers - repeating them here would need Docker-in-Docker for no
# extra safety, so this build just packages what CI already verified.
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Runs as a non-root user - the default Spring Boot layered image guidance,
# and there's no reason a Java process serving HTTP needs root inside its
# own container.
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /app/target/crm-platform-*.jar app.jar
USER spring:spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
