FROM eclipse-temurin:17-jre-alpine

# Create a non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Set working directory
WORKDIR /app

# Copy the built jar into the container
COPY build/libs/*.jar app.jar

# Ensure the non-root user owns the app directory
RUN chown -R appuser:appgroup /app

# Switch to the non-root user
USER appuser

# Run the Spring Boot application
# We use spring.config.import to load the .env file as application properties
# This prevents the credentials from being exposed in the container's OS environment
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.import=optional:file:/app/secrets/env.properties"]
