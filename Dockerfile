FROM maven:3.9.12-eclipse-temurin-25-alpine AS builder

WORKDIR /app

COPY pom.xml ./
COPY src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN addgroup -S edgar4j && adduser -S edgar4j -G edgar4j
RUN mkdir -p /app/data

COPY --from=builder /app/target/*.jar /app/app.jar

RUN chown -R edgar4j:edgar4j /app

USER edgar4j

VOLUME /app/data
EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"
ENV EDGAR4J_RESOURCE_MODE=high
ENV SPRING_CLOUD_CONFIG_ENABLED=false

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --edgar4j.resource-mode=${EDGAR4J_RESOURCE_MODE}"]
