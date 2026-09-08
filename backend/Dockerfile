# Multi-stage build, Java 21 in both stages - this is what pins the JDK
# version on Render (and everywhere else this image is built), independent
# of whatever JDK a developer has installed locally. Render's Docker
# runtime builds directly from this file; it does not read
# system.properties/.java-version, so no such file is needed elsewhere.

# Build stage - uses Maven image so no wrapper binary needs to be committed
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B package -DskipTests

# Runtime stage - slim JRE only, keeps the image small for faster cold
# starts on Render's free tier.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Informational only - Render injects its own PORT (defaults to 10000) at
# runtime regardless of this value, and the app binds to it dynamically
# (see server.port in application.yml). Kept at 8080 to match local
# docker-compose usage.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
