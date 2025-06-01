# syntax = docker/dockerfile:1.2
FROM clojure:openjdk-17 AS build

WORKDIR /
COPY . /

RUN clj -Sforce -T:build all

FROM azul/zulu-openjdk-alpine:17

# Install libstdc++, needed for RocksDB on Alpine
RUN apk add --no-cache libstdc++

COPY --from=build /target/kit-test-standalone.jar /kit-test/kit-test-standalone.jar

EXPOSE $PORT

ENTRYPOINT exec java $JAVA_OPTS -jar /kit-test/kit-test-standalone.jar
