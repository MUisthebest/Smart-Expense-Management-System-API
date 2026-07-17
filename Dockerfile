# ---- Build stage ----
# eclipse-temurin (Docker Hub) kept timing out through its CloudFront-backed blob storage
# on this network, so build/run both stages from Microsoft's registry instead (Azure CDN,
# separate infra). MCR only publishes a combined JDK image, not a JRE-only variant.
FROM mcr.microsoft.com/openjdk/jdk:17-ubuntu AS build
WORKDIR /app

# Cache dependencies separately from source so code edits don't re-download the world
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# ---- Runtime stage ----
FROM mcr.microsoft.com/openjdk/jdk:17-ubuntu AS runtime
WORKDIR /app

RUN groupadd -r spring && useradd -r -g spring spring
COPY --from=build /app/target/*.jar app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
