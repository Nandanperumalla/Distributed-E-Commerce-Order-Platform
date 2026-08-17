# One Dockerfile, three images — pass MODULE at build time.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the poms alone first so the dependency layer survives source edits.
COPY pom.xml ./
COPY common/pom.xml common/
COPY order-service/pom.xml order-service/
COPY inventory-service/pom.xml inventory-service/
COPY payment-service/pom.xml payment-service/
RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY common/src common/src
COPY order-service/src order-service/src
COPY inventory-service/src inventory-service/src
COPY payment-service/src payment-service/src

ARG MODULE
RUN mvn -B -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
ARG MODULE
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/${MODULE}/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
