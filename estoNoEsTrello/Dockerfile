# 1. Usar una imagen oficial de Java 17 ligera
FROM eclipse-temurin:17-jdk-alpine

# 2. Definir el directorio de trabajo dentro del contenedor
WORKDIR /app

# 3. Copiar el archivo JAR compilado desde la carpeta target
COPY target/EstoNoEsTrello-0.0.1-SNAPSHOT.jar app.jar

# 4. Exponer el puerto por defecto de Spring Boot
EXPOSE 8080

# 5. Comando para encender la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]