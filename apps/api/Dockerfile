# Local Compose signing-key initializer
FROM eclipse-temurin:25-jdk-alpine AS jwt-key-initializer

COPY docker/init-jwt-keys.sh /usr/local/bin/init-jwt-keys
RUN chmod +x /usr/local/bin/init-jwt-keys

ENTRYPOINT ["/usr/local/bin/init-jwt-keys"]

# Stage 1: Resolve build dependencies
FROM eclipse-temurin:25-jdk-alpine AS dependencies
WORKDIR /app

# Copy maven wrapper and dependencies descriptor
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Resolve dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Stage 2: Development server
FROM dependencies AS development

COPY src ./src

EXPOSE 8080

CMD ["./mvnw", "spring-boot:run"]

# Stage 3: Build the application
FROM dependencies AS builder

COPY src ./src
RUN ./mvnw package -DskipTests

# Stage 4: Production runner
FROM eclipse-temurin:25-jre-alpine AS runner
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
