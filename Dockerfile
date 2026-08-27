# syntax=docker/dockerfile:1

# --- Build stage (JDK 25 + Maven) ---
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --- Runtime stage (JRE 25) ---
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/target/reality-check-legacy.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
