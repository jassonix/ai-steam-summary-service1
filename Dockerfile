FROM eclipse-temurin:21-jdk-alpine

# Создаем рабочую папку
WORKDIR /app

# 3. Копируем твой скомпилированный JAR-файл в контейнер
# (Убедись, что ты сначала запустил 'mvn clean package')
COPY target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]