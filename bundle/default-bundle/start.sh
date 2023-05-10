#!/bin/bash

JAVA_OPTS="${JAVA_OPTS}"

# Explicitly set trust store location
if [[ -n "${JAVAX_NET_SSL_TRUSTSTORE}" ]]; then
  JAVA_OPTS="${JAVA_OPTS} -Djavax.net.ssl.trustStore=${JAVAX_NET_SSL_TRUSTSTORE}"
fi

# Explicitly set trust store password
if [[ -n "${JAVAX_NET_SSL_TRUSTSTOREPASSWORD}" ]]; then
  JAVA_OPTS="${JAVA_OPTS} -Djavax.net.ssl.trustStorePassword=${JAVAX_NET_SSL_TRUSTSTOREPASSWORD}"
fi

if [[ -n ${DEBUG_JVM_PRINT_JAVA_OPTS} ]]; then
  echo "Applied JVM options: $JAVA_OPTS"
fi

exec java ${JAVA_OPTS} -cp /opt/app/* io.camunda.connector.bundle.ConnectorRuntimeApplication
