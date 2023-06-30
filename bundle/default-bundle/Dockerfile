FROM eclipse-temurin:17.0.7_7-jre

RUN mkdir /opt/app
COPY target/*-with-dependencies.jar /opt/app/

# Using the start script from the base connector runtime image
COPY start.sh /start.sh
RUN chmod +x start.sh

ENTRYPOINT ["/start.sh"]