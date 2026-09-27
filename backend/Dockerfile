# Multi-stage build, Java 21 in both stages - this is what pins the JDK
# version on Render (and everywhere else this image is built), independent
# of whatever JDK a developer has installed locally. Render's Docker
# runtime builds directly from this file; it does not read
# system.properties/.java-version, so no such file is needed elsewhere.

# Build stage - uses Maven image so no wrapper binary needs to be committed
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Where to download from, and how long to wait - see the comment in the
# settings file. Retries and parallel downloads are set here with it.
COPY docker-maven-settings.xml .
ENV MAVEN_ARGS="-B -s docker-maven-settings.xml -Dmaven.artifact.threads=8 -Daether.connector.http.retryHandler.count=5"
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src src
RUN mvn package -DskipTests

# Runtime stage - slim JRE only.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar /tmp/sheout.jar
# Unpacked rather than run as one fat jar: classes load faster from plain
# jars on the classpath, and the class-data archive below needs it.
RUN java -Djarmode=tools -jar /tmp/sheout.jar extract --destination /app && rm /tmp/sheout.jar

# The JVM settings, used for the training run below and for the service.
# Measured on one core with the platform's load test (loadtest/README.md):
#  - A heap of 60% of the container. Left alone, Java takes 25%, which on a
#    512 MB instance is 128 MB: under a burst it spends its time collecting
#    garbage (startup 37 s -> 27 s, first-minute p95 2.7 s -> 1.6 s).
#  - Only the fast first-tier compiler. A new instance otherwise runs its
#    code interpreted until the optimising compiler has seen thousands of
#    calls, and on half a CPU that compiler competes with the requests: a
#    burst of bookings right after a deploy took 7.3 s at p95, against 0.3 s
#    with this. Peak speed of warm code is lower (booking p95 0.1 s -> 0.5 s
#    at the same load), and it uses ~90 MB less memory.
#  - Serial GC, which is what Java picks for one CPU anyway, stated here so
#    the training run and the service agree.
ENV SHEOUT_JVM_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

# Class-data sharing: start the application once at build time, up to the
# point its context is built, and save the classes it loaded; every real
# start then maps them instead of loading them (startup 13-14 s -> 9.5 s).
# There is no database or Redis here, so nothing may connect: migrations are
# off, Hibernate is told not to read the schema, and the URLs point nowhere.
# Best effort - if this fails for any reason the service still starts, only
# without the archive.
RUN (java -Xshare:dump > /dev/null 2>&1 || true) \
 && (DATABASE_URL=postgres://cds:cds@127.0.0.1:1/cds REDIS_URL=redis://127.0.0.1:1 \
     JWT_SECRET=class-data-training-run-only-not-a-secret SPRING_PROFILES_ACTIVE=render \
     java $SHEOUT_JVM_OPTS -XX:ArchiveClassesAtExit=/app/app.jsa -Xlog:cds=off -Xlog:cds+dynamic=off \
       -Dspring.context.exit=onRefresh -Dspring.flyway.enabled=false -Dspring.jpa.hibernate.ddl-auto=none \
       -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
       -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
       -jar /app/sheout.jar > /tmp/cds-training.log 2>&1 \
     || (echo "Class-data archive not built; starting without it." && tail -20 /tmp/cds-training.log))

# Informational only - Render injects its own PORT (defaults to 10000) at
# runtime regardless of this value, and the app binds to it dynamically
# (see server.port in application.yml). Kept at 8080 to match local
# docker-compose usage.
EXPOSE 8080
# A missing archive is only a warning to the JVM, which then starts normally.
ENTRYPOINT ["sh", "-c", "exec java $SHEOUT_JVM_OPTS -XX:SharedArchiveFile=/app/app.jsa -Xlog:cds=off -Xlog:cds+dynamic=off -jar /app/sheout.jar"]
