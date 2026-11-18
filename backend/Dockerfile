# ─── Stage 1: build the fat JAR ───────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Download dependencies first (cached layer unless pom.xml changes)
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q 2>/dev/null || true

# Copy source and build (skip tests; integration tests need Docker-in-Docker)
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q package -DskipTests && \
    mv target/*.jar /build/app.jar

# ─── Stage 2: slim runtime image ─────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine

# curl is used by the Docker healthcheck in docker-compose.full.yml.
# Run as an unprivileged user: a compromised app should not be root in the container.
RUN apk add --no-cache curl \
    && addgroup -S app \
    && adduser -S -G app app

WORKDIR /app
COPY --from=builder --chown=app:app /build/app.jar app.jar

USER app

EXPOSE 8080

# Respect container memory limits instead of the JVM guessing the host's RAM.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
