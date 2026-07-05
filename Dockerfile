# Multi-stage Docker build for IAM Platform.
# Produces a single image that runs as auth-server (port 8080) or
# admin-server (port 8081) depending on the SERVICE build arg.
#
# Build:
#   docker build --build-arg SERVICE=auth-server -t iam-auth-server .
#   docker build --build-arg SERVICE=admin    -t iam-admin      .

FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /build

# Maven deps cache layer — only re-download if pom.xml changes
COPY backend/pom.xml ./
COPY backend/iam-common/pom.xml iam-common/
COPY backend/iam-auth-server/pom.xml iam-auth-server/
COPY backend/iam-admin/pom.xml iam-admin/
RUN apt-get update && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*

# Build both JARs in one shot
COPY backend/src ./src
COPY backend/iam-common/src ./iam-common/src
COPY backend/iam-auth-server/src ./iam-auth-server/src
COPY backend/iam-admin/src ./iam-admin/src
RUN mvn -B -q -DskipTests package -pl iam-common,iam-${SERVICE} -am

# --- runtime ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
ARG JAR=auth-server
COPY --from=builder /build/backend/iam-${SERVICE}/target/boot/iam-${SERVICE}.jar app.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
EXPOSE ${PORT:-8080}

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=3 \
  CMD curl -sf http://localhost:${PORT:-8080}/iam/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=${SPRING_PROFILES:-prod}"]
