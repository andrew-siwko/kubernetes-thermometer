FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package

FROM alpine:3.19
WORKDIR /app
COPY --from=build /build/target/probe.war /app/probe.war
CMD ["cp", "/app/probe.war", "/config/apps/probe.war"]
