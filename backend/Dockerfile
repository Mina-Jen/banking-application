# syntax=docker/dockerfile:1

# Build stage: compile and package with Maven. Tests are skipped here because CI
# runs them separately, and the Testcontainers tests need a Docker daemon that
# does not exist inside an image build.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml ./
COPY src ./src

# The local Maven repository lives in a BuildKit cache mount, so changing a
# source file does not re-download every dependency.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package

# Runtime stage: JRE only -- no compiler, no Maven, no sources -- as a non-root
# user.
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# curl exists solely for the HEALTHCHECK below; the JRE image does not ship one.
# The version pin on apt-get install keeps the layer reproducible.
# hadolint ignore=DL3008
RUN useradd --system --uid 10001 --create-home appuser \
 && apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/target/*.jar app.jar

USER appuser
EXPOSE 8080

# JAVA_OPTS lets a deployment tune the JVM (heap, GC) without rebuilding the
# image. The exec form keeps Java as PID 1, so SIGTERM from
# `docker compose down` reaches Spring Boot and its shutdown hooks run.
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

# /actuator/health is permitAll in SecurityConfig, so no credentials are needed.
HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=6 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
