# syntax=docker/dockerfile:1
# Jovens Buzz API — multi-stage build. Java 17 matches <java.version> in pom.xml.

# ---------- Build stage: compile and package the Spring Boot jar ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies first, so they are cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src/main ./src/main
# Tests need a MySQL test database; run them before building the image (./mvnw test)
RUN mvn -B -q -Dmaven.test.skip=true package \
 && cp target/jovens_buzz-*.jar /build/app.jar

# ---------- Runtime stage: JRE only, non-root ----------
FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S jovens && adduser -S -G jovens -H -s /sbin/nologin jovens
WORKDIR /app
COPY --from=build --chown=jovens:jovens /build/app.jar /app/app.jar
USER jovens

# Configuration comes only from environment variables (no .env file exists in the image)
ENV PORT=8082 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"
EXPOSE 8082

# Redacted actuator health: UP only when the app and its database connection are healthy
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${PORT}/actuator/health" | grep -q '"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
