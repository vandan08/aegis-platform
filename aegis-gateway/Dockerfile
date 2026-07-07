# syntax=docker/dockerfile:1
# Multi-stage build for aegis-gateway (reactive/Netty). See aegis-auth-server/Dockerfile
# for the annotated version — the shape is identical across services.

# ---- build ----------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY aegis-auth-server/pom.xml aegis-auth-server/
COPY aegis-gateway/pom.xml aegis-gateway/
COPY aegis-resource-demo/pom.xml aegis-resource-demo/
RUN ./mvnw -q -pl aegis-gateway -am dependency:go-offline
COPY aegis-gateway/src aegis-gateway/src
RUN ./mvnw -q -pl aegis-gateway -am clean package -DskipTests

# ---- runtime --------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
RUN groupadd --system aegis && useradd --system --gid aegis --home /app aegis
COPY --from=build /workspace/aegis-gateway/target/*.jar app.jar
USER aegis
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD ["sh", "-c", "wget -qO- http://localhost:8080/actuator/health | grep -q '\"status\":\"UP\"' || exit 1"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
