# Stage 1: Resolve build dependencies
FROM eclipse-temurin:25-jdk-alpine AS dependencies
WORKDIR /app
ENV TZ=UTC
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"

# Copy maven wrapper and dependencies descriptor
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Resolve dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Stage 2: Build the application
FROM dependencies AS builder

COPY src ./src
RUN ./mvnw package -DskipTests -B

# Stage 3: Production runner
FROM eclipse-temurin:25-jre-alpine AS runner
WORKDIR /app
ENV TZ=UTC
# The JVM default heap is a quarter of the container limit; the API is the container's only process.
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC -XX:MaxRAMPercentage=75"

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
