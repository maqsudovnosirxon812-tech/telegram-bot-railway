# 1-bosqich: Maven bilan build qilish
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src

# Build the project (skip tests)
RUN mvn clean package -DskipTests

# Normalize jar filename so final stage can copy a predictable name
RUN set -e; \
    JAR=$(ls target/*-shaded.jar 2>/dev/null || ls target/*.jar 2>/dev/null || true); \
    if [ -z "$JAR" ]; then echo "No jar found in target/"; exit 1; fi; \
    cp "$JAR" target/app.jar

# 2-bosqich: faqat jar ni ishlatish
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]