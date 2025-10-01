FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY src ./src
COPY pom.xml .
COPY .env /app/.env
RUN mvn clean package -DskipTests

FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/.env .env
ENTRYPOINT ["java", "-jar", "app.jar"]
