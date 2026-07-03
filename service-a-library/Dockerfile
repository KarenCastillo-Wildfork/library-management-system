# --- Build stage ---
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Cache dependency resolution as its own layer, separate from source changes.
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/library-service.jar ./app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
