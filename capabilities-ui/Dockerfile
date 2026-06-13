# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml pom.xml
COPY capabilities-ui/pom.xml capabilities-ui/pom.xml
RUN --mount=type=cache,id=movie-stream-maven,target=/root/.m2,sharing=locked \
    mvn -B -f capabilities-ui/pom.xml -DskipTests dependency:go-offline

COPY capabilities-ui capabilities-ui
RUN --mount=type=cache,id=movie-stream-maven,target=/root/.m2,sharing=locked \
    mvn -B -f capabilities-ui/pom.xml -DskipTests package

FROM eclipse-temurin:25-jre
ENV JAVA_OPTS=""
WORKDIR /app
COPY --from=build /workspace/capabilities-ui/target/capabilities-ui-*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
