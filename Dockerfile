FROM gradle:8.10.2-jdk17 AS build

WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app

COPY --from=build --chown=app:app /workspace/build/libs/agent.jar /app/app.jar
COPY --chown=app:app protocols /app/protocols

USER app:app
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=3s --start-period=30s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
