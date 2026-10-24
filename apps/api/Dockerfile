FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu
WORKDIR /app
COPY --chown=app:app target/viridexa-api-0.1.0-SNAPSHOT.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
