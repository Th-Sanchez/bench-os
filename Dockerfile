# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /opt/benchos

RUN apk add --no-cache openssl \
    && addgroup -g 1000 -S benchos \
    && adduser -u 1000 -S benchos -G benchos

COPY --from=builder /workspace/target/*.jar /opt/benchos/app.jar
COPY docker-entrypoint.sh /opt/benchos/docker-entrypoint.sh
RUN sed -i 's/\r$//' /opt/benchos/docker-entrypoint.sh \
    && chmod 644 /opt/benchos/app.jar \
    && chmod 755 /opt/benchos/docker-entrypoint.sh

USER 1000:1000

ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom -Djava.io.tmpdir=/tmp"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- "http://localhost:${PORT}/actuator/health/readiness" | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["/opt/benchos/docker-entrypoint.sh"]
