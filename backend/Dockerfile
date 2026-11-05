# The backend as a jar on a JRE (ADR 062). Started by `docker compose --profile app up`.
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# The cache keeps downloaded dependencies between builds.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package \
    && mv target/*-SNAPSHOT.jar app.jar

FROM eclipse-temurin:21.0.12.1_1-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar
EXPOSE 8080
# Not root; 1000 is also the usual owner of the mounted document folder on a Linux host.
USER 1000:1000
ENTRYPOINT ["java", "-jar", "app.jar"]
