# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml pom.xml
COPY movies-api/pom.xml movies-api/pom.xml
RUN --mount=type=cache,id=movie-stream-maven,target=/root/.m2,sharing=locked \
    mvn -B -f movies-api/pom.xml -DskipTests dependency:go-offline

COPY movies-api/src movies-api/src
RUN --mount=type=cache,id=movie-stream-maven,target=/root/.m2,sharing=locked \
    mvn -B -f movies-api/pom.xml -DskipTests package

FROM eclipse-temurin:25-jre
ENV JAVA_OPTS=""
WORKDIR /app
COPY --from=build /workspace/movies-api/target/movies-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
