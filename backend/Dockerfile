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
FROM eclipse-temurin:21-jre-alpine

# curl is used by the Docker healthcheck in docker-compose.full.yml
RUN apk add --no-cache curl

WORKDIR /app
COPY --from=builder /build/app.jar app.jar

EXPOSE 8080

# The 'dev' profile is NOT active here; seeder is disabled by default.
# Override with -e SPRING_PROFILES_ACTIVE=dev for local testing.
ENTRYPOINT ["java", "-jar", "app.jar"]
