FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/shiye-admin-api-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 18081
ENTRYPOINT ["java", "-jar", "app.jar"]
