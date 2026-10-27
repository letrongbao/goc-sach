FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl openssl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 bookstore \
    && useradd --uid 10001 --gid bookstore --no-create-home bookstore \
    && mkdir -p /app/runtime && chown bookstore:bookstore /app/runtime
WORKDIR /app
COPY --from=build /build/target/goc-sach.jar /app/goc-sach.jar
COPY --chmod=755 docker/entrypoint.sh /app/entrypoint.sh
USER bookstore
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
