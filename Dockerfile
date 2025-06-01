# syntax = docker/dockerfile:1.2
FROM clojure:openjdk-17 AS build

WORKDIR /
COPY . /

RUN clj -Sforce -T:build all

FROM azul/zulu-openjdk-alpine:17

# Install libstdc++, needed for RocksDB on Alpine
RUN apk add --no-cache libstdc++

COPY --from=build /target/htmx-async-rendering-standalone.jar /my-app/htmx-async-rendering-standalone.jar

EXPOSE $PORT

ENTRYPOINT exec java $JAVA_OPTS -jar /my-app/htmx-async-rendering-standalone.jar
