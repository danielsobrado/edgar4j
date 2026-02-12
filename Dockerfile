FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw ./
COPY mvnw.cmd ./
COPY .mvn .mvn
COPY pom.xml ./

# Ensure wrapper script is executable and has Unix line endings
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN ./mvnw clean package -DskipTests

# Expose port
EXPOSE 8080

# Run application
CMD ["java", "-jar", "target/edgar4j-0.0.1-SNAPSHOT.jar"]
