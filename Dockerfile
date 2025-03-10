FROM eclipse-temurin:21-jre-ubi9-minimal
WORKDIR /home/container

LABEL org.opencontainers.image.source="https://github.com/e3ndr/tor-clearnet"

COPY ./server/target/tor-clearnet.jar /home/container

CMD [ "java", "-XX:+CrashOnOutOfMemoryError", "-jar", "tor-clearnet.jar" ]
EXPOSE 8000/tcp