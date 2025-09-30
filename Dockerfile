# 1-bosqich: Maven bilan build qilish
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src

# Build the project (skip tests)
RUN mvn clean package -DskipTests

# Normalize jar filename so final stage can copy a predictable name
# If shade plugin produces *-shaded.jar use it, otherwise take any jar in target
RUN set -e; \
    if [ -f target/*-shaded.jar ]; then \
      cp target/*-shaded.jar target/app.jar; \
    else \
      cp target/*.jar target/app.jar; \
    fi

# 2-bosqich: faqat jar ni ishlatish
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]