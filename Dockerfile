FROM openjdk:17-alpine
RUN apk add --update opus-tools
WORKDIR /app/com/example
COPY target/LlamaBot-1.0-SNAPSHOT.jar app.jar
COPY target/libs libs
COPY target/classes/vosk-model-small vosk-model-small
CMD ["java", "-jar", "/app/com/example/app.jar"]