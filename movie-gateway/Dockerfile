FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY . .
RUN mvn -B -pl movie-gateway -am -DskipTests package

FROM eclipse-temurin:25-jre
ENV JAVA_OPTS=""
WORKDIR /app
COPY --from=build /workspace/movie-gateway/target/movie-gateway-*.jar app.jar
EXPOSE 8079
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
