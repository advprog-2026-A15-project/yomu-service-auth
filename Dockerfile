# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY shared-lib ./shared-lib
COPY service-auth ./service-auth

ARG CLEAN_GRADLE_CACHE=0

# Build shared-lib
RUN --mount=type=cache,target=/root/.gradle,sharing=locked if [ "$CLEAN_GRADLE_CACHE" = "1" ]; then rm -rf /root/.gradle/caches /root/.gradle/wrapper/dists; fi && \
    cd ./shared-lib && \
    sed -i 's/\r$//' ./gradlew && \
    chmod +x ./gradlew && \
    ./gradlew publishToMavenLocal --no-daemon

# Build service
RUN --mount=type=cache,target=/root/.gradle,sharing=locked cd ./service-auth && \
    sed -i 's/\r$//' ./gradlew && \
    chmod +x ./gradlew && \
    ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/service-auth/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
