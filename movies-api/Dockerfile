FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY . .
RUN mvn -B -pl movies-api -am -DskipTests package

FROM eclipse-temurin:25-jre
ENV JAVA_OPTS=""
WORKDIR /app
COPY --from=build /workspace/movies-api/target/movies-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
