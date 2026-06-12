# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml pom.xml
COPY movie-gateway/pom.xml movie-gateway/pom.xml
RUN --mount=type=cache,id=movie-stream-maven,target=/root/.m2,sharing=locked \
    mvn -B -f movie-gateway/pom.xml -DskipTests dependency:go-offline

COPY movie-gateway/src movie-gateway/src
RUN --mount=type=cache,id=movie-stream-maven,target=/root/.m2,sharing=locked \
    mvn -B -f movie-gateway/pom.xml -DskipTests package

FROM eclipse-temurin:25-jre
ENV JAVA_OPTS=""
WORKDIR /app
COPY --from=build /workspace/movie-gateway/target/movie-gateway-*.jar app.jar
EXPOSE 8079
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
