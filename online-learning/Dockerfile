# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-alpine AS base
WORKDIR /app
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
COPY src ./src
RUN chmod +x ./mvnw && ./mvnw dependency:resolve

FROM base as test
WORKDIR /app
COPY --from=base /app/. .
RUN chmod +x ./mvnw && ./mvnw test

FROM base as build
WORKDIR /app
RUN chmod +x ./mvnw && ./mvnw clean install -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]