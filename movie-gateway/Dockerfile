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

FROM eclipse-temurin:25-jre AS extract
WORKDIR /workspace
COPY --from=build /workspace/movie-gateway/target/movie-gateway-*.jar app.jar
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
EXPOSE 8079
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
