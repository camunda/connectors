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
  echo "Applied JVM options: $JAVA_OPTS -XX:-UseBASE64Intrinsics"
fi

# Mitigate CVE-2026-114938 (Java4Shell): disable the AVX-512 Base64 decoder intrinsic.
# Placed after JAVA_OPTS so it cannot be overridden. Remove once the base JRE ships the fix.
exec java ${JAVA_OPTS} -XX:-UseBASE64Intrinsics -cp "/opt/app/*:/opt/custom/*" "io.camunda.connector.runtime.app.ConnectorRuntimeApplication"
