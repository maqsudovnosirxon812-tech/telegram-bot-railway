# 1-bosqich: Maven bilan build qilish
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# NOTE: Do NOT copy .env into the image during build — provide env at runtime
RUN mvn clean package -DskipTests

# 2-bosqich: faqat shaded jar ni ishlatish
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/untitled10-1.0-SNAPSHOT-shaded.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]