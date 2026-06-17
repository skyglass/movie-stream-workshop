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

FROM eclipse-temurin:25-jre AS extract
WORKDIR /workspace
COPY --from=build /workspace/movies-api/target/movies-api-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --launcher --layers dependencies --destination extracted \
    && java -Djarmode=tools -jar app.jar extract --launcher --layers spring-boot-loader --destination extracted --force \
    && java -Djarmode=tools -jar app.jar extract --launcher --layers snapshot-dependencies --destination extracted --force \
    && java -Djarmode=tools -jar app.jar extract --launcher --layers application --destination extracted --force

FROM eclipse-temurin:25-jre
ENV JAVA_OPTS=""
WORKDIR /app
COPY --from=extract --chown=1001:0 /workspace/extracted/dependencies/ ./
COPY --from=extract --chown=1001:0 /workspace/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=1001:0 /workspace/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=1001:0 /workspace/extracted/application/ ./
USER 1001
EXPOSE 8080
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
