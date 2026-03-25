FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN chmod +x mvnw
RUN ./mvnw clean install -DskipTests

# Exec form does not expand globs; jar name matches pom.xml <finalName>
EXPOSE 8080
ENV PORT=8080
CMD ["java", "-jar", "target/youngsters-sports-club.jar"]
