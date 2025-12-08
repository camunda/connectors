FROM reg.mini.dev/1212/openjre-base:25.0.1-dev

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk:
#FROM eclipse-temurin:25.0.1_8-jre

VOLUME /tmp

WORKDIR /

# Switch to root to allow setting up our own user and dirs
USER root

# The /opt/app is used for the Connectors runtime, and out-of-the-box connectors
# Use the /opt/custom to mount your own connectors, secret providers, or include other jars into the classpath
RUN mkdir /opt/app && mkdir /opt/custom

COPY target/*-with-dependencies.jar /opt/app/

# Using the start script from the base connector runtime image
COPY start.sh /start.sh
RUN chmod +x /start.sh

# Create an unprivileged user / group and switch to that user
RUN addgroup --gid 1001 camunda && adduser -S -G camunda -u 1001 --no-create-home camunda

USER 1001:1001

ENV CAMUNDA_CLIENT_AUTH_CREDENTIALS_CACHE_PATH=/tmp/connectors
ENTRYPOINT ["/start.sh"]
