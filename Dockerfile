# 1-bosqich: build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY .env .env
RUN mvn clean package -DskipTests

# 2-bosqich: run
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/untitled10-1.0-SNAPSHOT-shaded.jar app.jar
# agar .env kerak bo'lsa, build stage dan final stage ga o'tkazing:
COPY --from=build /app/.env .env
ENTRYPOINT ["java", "-jar", "app.jar"]