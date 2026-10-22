# 构建阶段使用 JDK 17（与 pom.xml 的 java.version 一致）
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app
RUN groupadd --system app \
    && useradd --system --gid app app \
    && mkdir -p /app/logs \
    && chown -R app:app /app

COPY --from=build /build/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar
USER app

EXPOSE 8080
ENV LOG_DIR=/app/logs
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
