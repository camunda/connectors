/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.apache.kafka.clients.CommonClientConfigs;
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
 * <p>Only an implausible response fails the check. Any other outcome (connection refused, timeout,
 * TLS handshake failure, closed connection) is left to the Kafka client to handle as before.
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

  private KafkaBootstrapProbe() {}

  static void verify(Properties kafkaProps) {
    verify(kafkaProps, DEFAULT_TIMEOUT_MS);
  }

  static void verify(Properties kafkaProps, int timeoutMs) {
    if (Boolean.parseBoolean(System.getenv(DISABLED_ENV))) {
      return;
    }
    Object servers = kafkaProps.get(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG);
    if (servers == null) {
      return;
    }
    Object protocol = kafkaProps.get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG);
    boolean tls =
        protocol != null && protocol.toString().trim().toUpperCase().matches("SSL|SASL_SSL");
    for (String server : servers.toString().split(",")) {
      String entry = server.trim();
      if (entry.isEmpty()) {
        continue;
      }
      String host = Utils.getHost(entry);
      Integer port = Utils.getPort(entry);
      if (host == null || port == null) {
        continue;
      }
      probe(host, port, tls, timeoutMs);
    }
  }

  private static void probe(String host, int port, boolean tls, int timeoutMs) {
    int responseSize;
    try (Socket raw = new Socket()) {
      raw.connect(new InetSocketAddress(host, port), timeoutMs);
      raw.setSoTimeout(timeoutMs);
      Socket socket = raw;
      if (tls) {
        SSLSocket sslSocket =
            (SSLSocket)
                ((SSLSocketFactory) SSLSocketFactory.getDefault())
                    .createSocket(raw, host, port, true);
        sslSocket.startHandshake();
        socket = sslSocket;
      }
      OutputStream out = socket.getOutputStream();
      out.write(apiVersionsRequest());
      out.flush();
      responseSize = new DataInputStream(socket.getInputStream()).readInt();
    } catch (EOFException e) {
      LOG.debug("Bootstrap probe of {}:{} inconclusive: connection closed", host, port);
      return;
    } catch (IOException | RuntimeException e) {
      LOG.debug("Bootstrap probe of {}:{} inconclusive: {}", host, port, e.toString());
      return;
    }
    if (responseSize >= 0 && responseSize <= MAX_PLAUSIBLE_RESPONSE_SIZE) {
      return;
    }
    throw new IllegalStateException(describe(host, port, tls, responseSize));
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
