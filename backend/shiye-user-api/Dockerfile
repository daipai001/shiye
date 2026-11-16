FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/shiye-user-api-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 18080
ENTRYPOINT ["java", "-jar", "app.jar"]
