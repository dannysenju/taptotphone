# Stage 1: Build the Maven project
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build jar
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Production distroless-like JRE container
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add non-privileged user for security compliance (PCI DSS recommendation)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy compile output from builder stage
COPY --from=builder /build/target/taptotphone-0.0.1-SNAPSHOT.jar app.jar

# Expose ports
# 8080: REST API, 6000: jPOS TCP Server
EXPOSE 8080 6000

# Optimize JVM for Java 21 Virtual Threads and high throughput
ENTRYPOINT ["java", \
            "-XX:+UseG1GC", \
            "-XX:+UseNUMA", \
            "-XX:+UnlockExperimentalVMOptions", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", \
            "app.jar"]
