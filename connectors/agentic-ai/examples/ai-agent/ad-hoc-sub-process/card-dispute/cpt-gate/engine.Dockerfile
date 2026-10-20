FROM eclipse-temurin:21-jre
WORKDIR /usr/local/camunda
COPY --chown=1001:1001 dist/bin/ bin/
COPY --chown=1001:1001 dist/lib/ lib/
COPY --chown=1001:1001 dist/config/ config/
RUN mkdir data logs && chown 1001:1001 data logs && chmod +x bin/camunda
USER 1001:1001
EXPOSE 8080 26500 26501 26502 9600
ENTRYPOINT ["/usr/local/camunda/bin/camunda"]
