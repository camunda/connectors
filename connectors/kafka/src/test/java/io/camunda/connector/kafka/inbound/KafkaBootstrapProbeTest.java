/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.inbound;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Consumer;
import org.apache.kafka.clients.CommonClientConfigs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class KafkaBootstrapProbeTest {

  private static final int TIMEOUT_MS = 2000;

  private ServerSocket server;
  private Thread serverThread;

  @AfterEach
  void tearDown() throws Exception {
    if (server != null) {
      server.close();
    }
    if (serverThread != null) {
      serverThread.join(TIMEOUT_MS);
    }
  }

  @Test
  void shouldFailWhenServerAnswersWithHttp() throws Exception {
    int port = startServer(out -> write(out, "HTTP/1.1 400 Bad Request\r\n\r\n".getBytes()));

    assertThatThrownBy(() -> KafkaBootstrapProbe.verify(props("localhost:" + port), TIMEOUT_MS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("localhost:" + port)
        .hasMessageContaining("1213486160")
        .hasMessageContaining("HTTP(S) endpoint")
        .hasMessageContaining("9092");
  }

  @Test
  void shouldFailWithTlsHintWhenPlaintextClientHitsTlsPort() throws Exception {
    // TLS alert record: content type 0x15, version 0x0303, length 2, fatal decode_error
    int port =
        startServer(out -> write(out, new byte[] {0x15, 0x03, 0x03, 0x00, 0x02, 0x02, 0x32}));

    assertThatThrownBy(() -> KafkaBootstrapProbe.verify(props("localhost:" + port), TIMEOUT_MS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TLS record");
  }

  @Test
  void shouldPassWhenServerAnswersWithPlausibleKafkaFrame() throws Exception {
    int port = startServer(out -> write(out, ByteBuffer.allocate(8).putInt(4).putInt(1).array()));

    assertThatCode(() -> KafkaBootstrapProbe.verify(props("localhost:" + port), TIMEOUT_MS))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldPassWhenServerClosesConnection() throws Exception {
    int port = startServer(out -> {});

    assertThatCode(() -> KafkaBootstrapProbe.verify(props("localhost:" + port), TIMEOUT_MS))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldPassWhenServerIsUnreachable() throws Exception {
    int port;
    try (ServerSocket unused = new ServerSocket(0)) {
      port = unused.getLocalPort();
    }

    assertThatCode(() -> KafkaBootstrapProbe.verify(props("localhost:" + port), TIMEOUT_MS))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldPassWhenTlsHandshakeFails() throws Exception {
    int port = startServer(false, out -> write(out, "HTTP/1.1 400 Bad Request\r\n\r\n".getBytes()));
    var props = props("localhost:" + port);
    props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");

    // Not a TLS server, so the handshake fails and the result is left to the Kafka client
    assertThatCode(() -> KafkaBootstrapProbe.verify(props, TIMEOUT_MS)).doesNotThrowAnyException();
  }

  @Test
  void shouldFailWhenAnyOfSeveralBootstrapServersIsNotKafka() throws Exception {
    int port = startServer(out -> write(out, "HTTP/1.1 400 Bad Request\r\n\r\n".getBytes()));
    int unreachable;
    try (ServerSocket unused = new ServerSocket(0)) {
      unreachable = unused.getLocalPort();
    }

    assertThatThrownBy(
            () ->
                KafkaBootstrapProbe.verify(
                    props(" localhost:" + unreachable + " , PLAINTEXT://localhost:" + port + ","),
                    TIMEOUT_MS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("localhost:" + port);
  }

  @Test
  void shouldSkipEntriesWithoutPort() {
    assertThatCode(() -> KafkaBootstrapProbe.verify(props("localhost"), TIMEOUT_MS))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldPassWithoutBootstrapServers() {
    assertThatCode(() -> KafkaBootstrapProbe.verify(new Properties(), TIMEOUT_MS))
        .doesNotThrowAnyException();
  }

  private static Properties props(String bootstrapServers) {
    var props = new Properties();
    props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    return props;
  }

  private static void write(OutputStream out, byte[] bytes) {
    try {
      out.write(bytes);
      out.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private int startServer(Consumer<OutputStream> handler) throws IOException {
    return startServer(true, handler);
  }

  // Accepts one connection, optionally reads the probe's request and lets the handler answer
  private int startServer(boolean readRequest, Consumer<OutputStream> handler) throws IOException {
    server = new ServerSocket(0);
    serverThread =
        new Thread(
            () -> {
              try (Socket socket = server.accept()) {
                if (!readRequest) {
                  handler.accept(socket.getOutputStream());
                  return;
                }
                var in = new DataInputStream(socket.getInputStream());
                byte[] request = new byte[in.readInt()];
                in.readFully(request);
                // api_key 18 (ApiVersions), api_version 0
                if (request[0] != 0 || request[1] != 18) {
                  throw new IllegalStateException(
                      "Unexpected request: " + new String(request, StandardCharsets.ISO_8859_1));
                }
                handler.accept(socket.getOutputStream());
              } catch (IOException ignored) {
                // server closed or client gone
              }
            });
    serverThread.start();
    return server.getLocalPort();
  }
}
