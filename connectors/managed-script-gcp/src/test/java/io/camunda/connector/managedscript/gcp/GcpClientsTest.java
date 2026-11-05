/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GcpClientsTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(1);

  private HttpServer server;
  private final CountDownLatch release = new CountDownLatch(1);
  private final HttpClient http = GcpClients.http();

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/stalled-body",
        exchange -> {
          // The headers and a part of the body, then nothing until the test ends.
          exchange.sendResponseHeaders(200, 1000);
          exchange.getResponseBody().write(new byte[10]);
          exchange.getResponseBody().flush();
          try {
            release.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          exchange.close();
        });
    server.createContext(
        "/ok",
        exchange -> {
          final var body = "done".getBytes();
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    release.countDown();
    server.stop(0);
    http.close();
  }

  @Test
  void returnsAResponseThatCompletesInTime() throws Exception {
    final var response = GcpClients.send(http, get("/ok"), HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("done");
  }

  @Test
  void boundsTheWholeExchangeWhenTheBodyStalls() {
    final long start = System.nanoTime();

    assertThatThrownBy(
            () -> GcpClients.send(http, get("/stalled-body"), HttpResponse.BodyHandlers.ofString()))
        .isInstanceOf(HttpTimeoutException.class);

    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
  }

  @Test
  void boundsAnUploadWhoseBodyStalls() {
    assertThatThrownBy(() ->
            GcpClients.send(http, get("/stalled-body"), HttpResponse.BodyHandlers.discarding()))
        .isInstanceOf(HttpTimeoutException.class);
  }

  private HttpRequest get(String path) {
    return HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path))
        .timeout(TIMEOUT)
        .GET()
        .build();
  }
}
