FROM eclipse-temurin:25-jre

WORKDIR /app

# Copy the jar file
COPY target/cms-form-extractor-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
