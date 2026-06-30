# ponytail: one Dockerfile, two build args select which service jar to package.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY iam-common/pom.xml iam-common/
COPY iam-auth-server/pom.xml iam-auth-server/
COPY iam-admin/pom.xml iam-admin/
RUN mvn -B -q -pl iam-common -am install
COPY iam-common/src ./iam-common/src
COPY iam-auth-server/src ./iam-auth-server/src
COPY iam-admin/src ./iam-admin/src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
ARG SERVICE=iam-auth-server
COPY --from=build /app/${SERVICE}/target/${SERVICE}-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
