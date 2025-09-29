# Java 17 asosida image
FROM openjdk:17-jdk-slim

# App jar faylini konteynerga ko‘chiramiz
WORKDIR /app
COPY target/*.jar app.jar

# Start komandasi
ENTRYPOINT ["java", "-jar", "app.jar"]
