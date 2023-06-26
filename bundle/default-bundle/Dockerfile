FROM eclipse-temurin:17.0.7_7-jre-jammy

RUN mkdir /opt/app
# Download connectors from maven central
COPY target/*-with-dependencies.jar /opt/app/

COPY start.sh /start.sh
RUN chmod +x start.sh

ENTRYPOINT ["/start.sh"]