FROM maven:3.9.9-eclipse-temurin-17

WORKDIR /app

COPY . .

RUN mvn clean install -DskipTests

CMD ["sh", "-c", "java -jar target/*.jar"]
