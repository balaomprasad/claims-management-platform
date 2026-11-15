# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 claimsphere \
    && useradd --system --uid 10001 --gid claimsphere --no-create-home claimsphere \
    && mkdir -p /data/uploads && chown 10001:10001 /data/uploads
WORKDIR /app
COPY --from=build /workspace/target/claimsphere-backend.jar app.jar
# Numeric UID so Kubernetes runAsNonRoot can verify the user.
USER 10001
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    STORAGE_DIR=/data/uploads
VOLUME /data/uploads
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
