# syntax=docker/dockerfile:1
# Production image only. Spring profile is always `prod`.

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src

COPY pom.xml .
COPY src ./src

ARG BUILD_VERSION=0.0.0
ARG GIT_SHA=unknown

RUN mvn -B package \
    && mv target/education-base.jar /src/app.jar

FROM eclipse-temurin:21-jre-jammy AS production
WORKDIR /app

ARG BUILD_VERSION=0.0.0
ARG GIT_SHA=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="education-api" \
      org.opencontainers.image.description="EDUCATION API production image" \
      org.opencontainers.image.version="${BUILD_VERSION}" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.created="${BUILD_DATE}"

RUN groupadd --system education && useradd --system --gid education --uid 10001 education \
    && mkdir -p /app/outputs \
    && chown -R education:education /app

COPY --from=build --chown=education:education /src/app.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    APP_STORAGE_DIR=/app/outputs \
    BUILD_VERSION=${BUILD_VERSION} \
    GIT_SHA=${GIT_SHA} \
    JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"

USER education
EXPOSE 8080
VOLUME ["/app/outputs"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
