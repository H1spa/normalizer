FROM openjdk:27-ea-21-slim-bookworm
WORKDIR /app
COPY target/normalizer-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
