/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import javax.net.ssl.SSLSocket;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.network.ConnectionMode;
import org.apache.kafka.common.security.ssl.DefaultSslEngineFactory;
import org.apache.kafka.common.security.ssl.SslFactory;
import org.apache.kafka.common.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks that every bootstrap server speaks the Kafka protocol before a consumer is created.
 *
 * <p>The Kafka client reads the first 4 bytes of a response as the frame length and allocates a
 * buffer of that size without any upper bound. A non-Kafka endpoint (e.g. an HTTPS endpoint on port
 * 443 answering {@code HTTP/1.1 400}) makes the client allocate ~1.2 GB, which crashes the whole
 * runtime with an {@link OutOfMemoryError}. This probe sends a minimal ApiVersions request, reads
 * only the length prefix and fails if it is implausible.
 *
 * <p>TLS uses the connector's own {@code ssl.*} settings, like the Kafka client, so the probe's
 * handshake succeeds whenever the consumer's would.
 *
 * <p>Only an implausible response fails the check. Any other outcome (connection refused, timeout,
 * TLS handshake failure, closed connection, invalid configuration) is left to the Kafka client to
 * handle as before.
 */
final class KafkaBootstrapProbe {

  static final String DISABLED_ENV = "CAMUNDA_CONNECTOR_KAFKA_BOOTSTRAP_PROBE_DISABLED";

  private static final Logger LOG = LoggerFactory.getLogger(KafkaBootstrapProbe.class);

  // An ApiVersions response is a few hundred bytes
  static final int MAX_PLAUSIBLE_RESPONSE_SIZE = 1024 * 1024;

  private static final int HTTP_MAGIC = 0x48545450; // "HTTP"
  private static final byte TLS_ALERT = 0x15;
  private static final byte TLS_HANDSHAKE = 0x16;
  private static final short API_VERSIONS_KEY = 18;
  private static final byte[] CLIENT_ID =
      "camunda-connectors-bootstrap-probe".getBytes(StandardCharsets.UTF_8);
  private static final int DEFAULT_TIMEOUT_MS = 5000;

  // Parses the settings the same way as the Kafka client, e.g. bootstrap.servers as a String or a
  // List
  private static final ConfigDef PROBE_CONFIG =
      new ConfigDef()
          .define(
              CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
              ConfigDef.Type.LIST,
              List.of(),
              ConfigDef.Importance.HIGH,
              CommonClientConfigs.BOOTSTRAP_SERVERS_DOC)
          .define(
              CommonClientConfigs.SECURITY_PROTOCOL_CONFIG,
              ConfigDef.Type.STRING,
              CommonClientConfigs.DEFAULT_SECURITY_PROTOCOL,
              ConfigDef.Importance.MEDIUM,
              CommonClientConfigs.SECURITY_PROTOCOL_DOC)
          .withClientSslSupport();

  private KafkaBootstrapProbe() {}

  static void verify(Properties kafkaProps) {
    verify(kafkaProps, DEFAULT_TIMEOUT_MS);
  }

  static void verify(Properties kafkaProps, int timeoutMs) {
    if (Boolean.parseBoolean(System.getenv(DISABLED_ENV))) {
      return;
    }
    AbstractConfig config;
    try {
      config = new AbstractConfig(PROBE_CONFIG, kafkaProps, false);
    } catch (ConfigException e) {
      LOG.debug("Bootstrap probe skipped: {}", e.getMessage());
      return;
    }
    String protocol = config.getString(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG);
    boolean tls = protocol.trim().toUpperCase(Locale.ROOT).matches("SSL|SASL_SSL");
    SslFactory sslFactory = null;
    if (tls) {
      sslFactory = sslFactory(config);
      if (sslFactory == null) {
        return;
      }
    }
    try {
      for (String server : config.getList(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG)) {
        String entry = server.trim();
        if (entry.isEmpty()) {
          continue;
        }
        String host = Utils.getHost(entry);
        Integer port = Utils.getPort(entry);
        if (host == null || port == null) {
          continue;
        }
        probe(host, port, sslFactory, timeoutMs);
      }
    } finally {
      if (sslFactory != null) {
        sslFactory.close();
      }
    }
  }

  private static SslFactory sslFactory(AbstractConfig config) {
    SslFactory sslFactory = new SslFactory(ConnectionMode.CLIENT);
    try {
      sslFactory.configure(config.values());
    } catch (RuntimeException e) {
      sslFactory.close();
      LOG.debug("Bootstrap probe skipped: invalid SSL configuration: {}", e.toString());
      return null;
    }
    // A custom ssl.engine.factory.class has no SSLContext to create a socket from
    if (!(sslFactory.sslEngineFactory() instanceof DefaultSslEngineFactory)) {
      sslFactory.close();
      LOG.debug("Bootstrap probe skipped: custom SSL engine factory");
      return null;
    }
    return sslFactory;
  }

  private static void probe(String host, int port, SslFactory sslFactory, int timeoutMs) {
    byte[] prefix;
    try (Socket raw = new Socket()) {
      raw.connect(new InetSocketAddress(host, port), timeoutMs);
      raw.setSoTimeout(timeoutMs);
      if (sslFactory == null) {
        prefix = exchange(raw);
      } else {
        try (SSLSocket socket = startTls(raw, host, port, sslFactory)) {
          prefix = exchange(socket);
        }
      }
    } catch (IOException | RuntimeException e) {
      LOG.debug("Bootstrap probe of {}:{} inconclusive: {}", host, port, e.toString());
      return;
    }
    if (prefix.length < Integer.BYTES) {
      LOG.debug("Bootstrap probe of {}:{} inconclusive: connection closed", host, port);
      return;
    }
    int responseSize = ByteBuffer.wrap(prefix).getInt();
    if (responseSize >= 0 && responseSize <= MAX_PLAUSIBLE_RESPONSE_SIZE) {
      return;
    }
    throw new IllegalStateException(describe(host, port, sslFactory != null, responseSize));
  }

  // Uses the SSLContext and SSL parameters (protocols, cipher suites, hostname verification) the
  // Kafka client would use for this server
  private static SSLSocket startTls(Socket raw, String host, int port, SslFactory sslFactory)
      throws IOException {
    var engineFactory = (DefaultSslEngineFactory) sslFactory.sslEngineFactory();
    var socket =
        (SSLSocket)
            engineFactory.sslContext().getSocketFactory().createSocket(raw, host, port, true);
    socket.setSSLParameters(sslFactory.createSslEngine(host, port).getSSLParameters());
    socket.startHandshake();
    return socket;
  }

  // Sends the request and reads at most the 4-byte length prefix of the response
  private static byte[] exchange(Socket socket) throws IOException {
    OutputStream out = socket.getOutputStream();
    out.write(apiVersionsRequest());
    out.flush();
    return socket.getInputStream().readNBytes(Integer.BYTES);
  }

  private static String describe(String host, int port, boolean tls, int responseSize) {
    String message =
        String.format(
            "Bootstrap server %s:%d does not speak the Kafka protocol: the response length prefix is %d bytes, which the Kafka client would try to allocate.",
            host, port, responseSize);
    if (responseSize == HTTP_MAGIC) {
      return message
          + " The server answered with an HTTP response, so this is an HTTP(S) endpoint, not a Kafka broker. Check the bootstrap server port (Confluent Cloud brokers use port 9092, not 443).";
    }
    byte firstByte = (byte) (responseSize >>> 24);
    if (!tls && (firstByte == TLS_ALERT || firstByte == TLS_HANDSHAKE)) {
      return message
          + " The server answered with a TLS record, so it expects an encrypted connection. Check the security protocol (e.g. SASL_SSL).";
    }
    return message + " Check the bootstrap server address and port.";
  }

  // ApiVersions request v0 with request header v1: size, api_key, api_version, correlation_id,
  // client_id. All brokers accept it before authentication.
  private static byte[] apiVersionsRequest() {
    int size = 2 + 2 + 4 + 2 + CLIENT_ID.length;
    return ByteBuffer.allocate(4 + size)
        .putInt(size)
        .putShort(API_VERSIONS_KEY)
        .putShort((short) 0)
        .putInt(1)
        .putShort((short) CLIENT_ID.length)
        .put(CLIENT_ID)
        .array();
  }
}
