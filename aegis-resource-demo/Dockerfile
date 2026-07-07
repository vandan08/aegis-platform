# syntax=docker/dockerfile:1
# Multi-stage build for aegis-resource-demo. See aegis-auth-server/Dockerfile for the
# annotated version — the shape is identical across services.

# ---- build ----------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY aegis-auth-server/pom.xml aegis-auth-server/
COPY aegis-gateway/pom.xml aegis-gateway/
COPY aegis-resource-demo/pom.xml aegis-resource-demo/
RUN ./mvnw -q -pl aegis-resource-demo -am dependency:go-offline
COPY aegis-resource-demo/src aegis-resource-demo/src
RUN ./mvnw -q -pl aegis-resource-demo -am clean package -DskipTests

# ---- runtime --------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
RUN groupadd --system aegis && useradd --system --gid aegis --home /app aegis
COPY --from=build /workspace/aegis-resource-demo/target/*.jar app.jar
USER aegis
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD ["sh", "-c", "wget -qO- http://localhost:8081/actuator/health | grep -q '\"status\":\"UP\"' || exit 1"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
