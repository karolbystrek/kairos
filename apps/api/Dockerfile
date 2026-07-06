# Stage 1: Build the application
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

# Copy maven wrapper and dependencies descriptor
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Resolve dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and build package
COPY src ./src
RUN ./mvnw package -DskipTests

# Stage 2: Create the runner image
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
