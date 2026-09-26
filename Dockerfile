FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/parallel-volunteer-admission-system-2.0.0.jar app.jar
ENV SERVER_ADDRESS=0.0.0.0 PORT=8088
EXPOSE 8088
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
