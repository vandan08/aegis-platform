# syntax=docker/dockerfile:1
# Multi-stage build for aegis-auth-server.
# Stage 1 builds the fat jar with the Maven wrapper (layered by Spring Boot).
# Stage 2 is a slim JRE image running as a non-root user.

# ---- build ----------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy just the wrapper + POMs first so dependency resolution is cached across
# source-only changes (Docker layer caching).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY aegis-auth-server/pom.xml aegis-auth-server/
COPY aegis-gateway/pom.xml aegis-gateway/
COPY aegis-resource-demo/pom.xml aegis-resource-demo/
RUN ./mvnw -q -pl aegis-auth-server -am dependency:go-offline

# Now the sources, then build only this module (+ its local deps).
COPY aegis-auth-server/src aegis-auth-server/src
RUN ./mvnw -q -pl aegis-auth-server -am clean package -DskipTests

# ---- runtime --------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Run as an unprivileged user (never root in a container).
RUN groupadd --system aegis && useradd --system --gid aegis --home /app aegis
COPY --from=build /workspace/aegis-auth-server/target/*.jar app.jar
USER aegis

EXPOSE 9000
# Fail fast if the app is unhealthy; Spring exposes /actuator/health/readiness.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD ["sh", "-c", "wget -qO- http://localhost:9000/actuator/health | grep -q '\"status\":\"UP\"' || exit 1"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
