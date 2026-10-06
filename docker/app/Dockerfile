FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY app/build/libs/bank-api-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]