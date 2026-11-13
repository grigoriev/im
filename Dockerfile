# The image of the Invoice Manager. CI builds the jar first: mvn verify.
FROM eclipse-temurin:25-jre-noble@sha256:e3b6f7887a50f20d8f07abf279d8a81a5953fb398a9555bd9edd56b9bd15968c

RUN groupadd --system --gid 10001 im \
    && useradd --system --uid 10001 --gid im --home-dir /opt/im --shell /usr/sbin/nologin im

WORKDIR /opt/im
COPY --chown=10001:10001 target/kosit-xrechnung /opt/im/kosit-xrechnung
COPY --chown=10001:10001 target/im.jar /opt/im/im.jar

USER 10001:10001
EXPOSE 8080

# The JRE image has no curl. Bash reads the health endpoint over /dev/tcp.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=3 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Dim.kosit.xrechnung-dir=/opt/im/kosit-xrechnung", "-jar", "/opt/im/im.jar"]
