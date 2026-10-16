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

# The development image watches mounted sources; production needs only the JRE.
FROM dependencies AS development
RUN apk add --no-cache watchexec
COPY src ./src
EXPOSE 8080
CMD ["watchexec", "--restart", "--poll", "1s", "--watch", "src/main", "--watch", "pom.xml", "--", "./mvnw", "--batch-mode", "-Dmaven.test.skip=true", "clean", "spring-boot:run"]

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

RUN addgroup -S kairos && adduser -S -G kairos -H -s /sbin/nologin kairos
COPY --from=builder /app/target/*.jar app.jar
USER kairos

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
