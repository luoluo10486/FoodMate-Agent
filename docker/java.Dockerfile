FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY . .
RUN mvn -B -pl foodmate-bootstrap -am -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/foodmate-bootstrap/target/foodmate-bootstrap-0.1.0-SNAPSHOT.jar /app/foodmate.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/foodmate.jar"]
