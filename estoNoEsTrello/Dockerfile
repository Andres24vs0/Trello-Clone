FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY target/EstoNoEsTrello-0.0.1-SNAPSHOT.jar /app/app.jar

ENV JAVA_OPTS=""

EXPOSE 8080 9010

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp /app/app.jar:/app org.springframework.boot.loader.launch.JarLauncher"]