FROM maven:3.9-eclipse-temurin-25 AS driver-fetch
WORKDIR /tmp
RUN mvn dependency:get -Dartifact=org.postgresql:postgresql:42.7.4:jar -Ddest=postgresql-42.7.4.jar

FROM icr.io/appcafe/open-liberty:full-java25-openj9-ubi-minimal
ARG BUILD_NUMBER=unknown
ENV BUILD_NUMBER=${BUILD_NUMBER}

COPY --chown=1001:0 liberty/server.xml /config/server.xml
COPY --from=driver-fetch --chown=1001:0 /tmp/postgresql-42.7.4.jar /config/resources/postgresql/

# Pre-warm server configuration and remove baked security artifacts to force
# fresh runtime keystore generation with KEYSTORE_PASSWORD
RUN configure.sh && rm -rf /opt/ol/wlp/output/defaultServer/resources/security
