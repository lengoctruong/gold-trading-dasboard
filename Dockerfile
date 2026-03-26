# ============================================
# Stage 1: Build
# ============================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy and cache dependencies first (layer caching)
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn -B -q -DskipTests clean package

# ============================================
# Stage 2: Runtime
# ============================================
FROM eclipse-temurin:21-jre-alpine

# Create non-root user for security
RUN addgroup -g 1001 appgroup && \
    adduser -D -u 1001 -G appgroup appuser

WORKDIR /app

# Copy JAR from build stage
COPY --from=build --chown=appuser:appgroup /app/target/*.jar app.jar

# Create runtime directories expected by the app (for example logback file appenders)
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose application port (8088 for dashboard)
EXPOSE 8088

# Health check (Spring Boot Actuator)
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8088/actuator/health || exit 1

# JVM options optimized for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Start application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
