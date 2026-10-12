/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.box.sdkgen.box.developertokenauth.BoxDeveloperTokenAuth;
import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.managers.uploads.UploadFileRequestBody;
import com.box.sdkgen.managers.uploads.UploadFileRequestBodyAttributesField;
import com.box.sdkgen.managers.uploads.UploadFileRequestBodyAttributesParentField;
import com.box.sdkgen.networking.baseurls.BaseUrls;
import com.box.sdkgen.networking.boxnetworkclient.BoxNetworkClient;
import com.box.sdkgen.networking.fetchoptions.FetchOptions;
import com.box.sdkgen.networking.fetchoptions.MultipartItem;
import com.box.sdkgen.networking.fetchresponse.FetchResponse;
import com.box.sdkgen.networking.retries.RetryStrategy;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.ConnectionSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BoxNetworkSessionTest {

  private final AtomicInteger uploadRequests = new AtomicInteger();
  private final AtomicInteger metadataRequests = new AtomicInteger();
  private HttpServer server;
  private BoxClient client;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/upload",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          uploadRequests.incrementAndGet();
          exchange.getResponseHeaders().add("Retry-After", "0");
          exchange.sendResponseHeaders(503, -1);
          exchange.close();
        });
    server.createContext(
        "/api",
        exchange -> {
          boolean first = metadataRequests.incrementAndGet() == 1;
          if (first) {
            exchange.getResponseHeaders().add("Retry-After", "0");
          }
          byte[] body = "{\"id\":\"1\",\"type\":\"file\"}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(first ? 503 : 200, first ? -1 : body.length);
          if (!first) {
            exchange.getResponseBody().write(body);
          }
          exchange.close();
        });
    server.start();
    String base = "http://localhost:" + server.getAddress().getPort();
    var session =
        BoxNetworkSession.create(
                BoxNetworkClient.getDefaultOkHttpClientBuilder()
                    .connectionSpecs(List.of(ConnectionSpec.CLEARTEXT)))
            .withCustomBaseUrls(
                new BaseUrls.Builder().baseUrl(base + "/api").uploadUrl(base + "/upload").build());
    client =
        new BoxClient.Builder(new BoxDeveloperTokenAuth("token")).networkSession(session).build();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void doesNotResendAStreamedUploadOnA503() {
    var body =
        new UploadFileRequestBody(
            new UploadFileRequestBodyAttributesField(
                "file.txt", new UploadFileRequestBodyAttributesParentField("0")),
            new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));

    assertThatThrownBy(() -> client.uploads.uploadFile(body)).isInstanceOf(RuntimeException.class);

    assertThat(uploadRequests).hasValue(1);
  }

  @Test
  void keepsTheSdkDefaultTimeouts() {
    var timeouts = new com.box.sdkgen.networking.network.NetworkSession().getTimeoutConfig();
    var client = (BoxNetworkClient) BoxNetworkSession.create().getNetworkClient();

    assertThat(client.getHttpClient().readTimeoutMillis())
        .isEqualTo(timeouts.getReadTimeoutMs().intValue());
    assertThat(client.getHttpClient().connectTimeoutMillis())
        .isEqualTo(timeouts.getConnectionTimeoutMs().intValue());
  }

  @Test
  void stillRetriesRequestsWithoutAStreamedBody() {
    var file = client.files.getFileById("1");

    assertThat(file.getId()).isEqualTo("1");
    assertThat(metadataRequests).hasValue(2);
  }

  @Test
  void neverRetriesRequestsStreamingABody() {
    var strategy = new BoxNetworkSession.StreamSafeRetryStrategy(alwaysRetry());
    var multipart = new FetchOptions.Builder("https://upload", "POST").build();
    multipart.multipartData = List.of(new MultipartItem("file"));

    assertThat(strategy.shouldRetry(multipart, new FetchResponse(503, Map.of()), 1)).isFalse();
  }

  @Test
  void closesDiscardedContentWhenRetrying() {
    var closed = new AtomicBoolean();
    var response = new FetchResponse(503, Map.of());
    response.content = trackingStream(closed);
    var strategy = new BoxNetworkSession.StreamSafeRetryStrategy(alwaysRetry());

    assertThat(strategy.shouldRetry(plainOptions(), response, 1)).isTrue();
    assertThat(closed).isTrue();
  }

  @Test
  void keepsContentOpenWhenNotRetrying() {
    var closed = new AtomicBoolean();
    var response = new FetchResponse(200, Map.of());
    response.content = trackingStream(closed);
    var strategy =
        new BoxNetworkSession.StreamSafeRetryStrategy(
            new RetryStrategy() {
              public boolean shouldRetry(FetchOptions o, FetchResponse r, int a) {
                return false;
              }

              public double retryAfter(FetchOptions o, FetchResponse r, int a) {
                return 0;
              }
            });

    assertThat(strategy.shouldRetry(plainOptions(), response, 1)).isFalse();
    assertThat(closed).isFalse();
  }

  @Test
  void closesDiscardedContentWhenTheDelegateThrows() {
    var closed = new AtomicBoolean();
    var response = new FetchResponse(401, Map.of());
    response.content = trackingStream(closed);
    var strategy =
        new BoxNetworkSession.StreamSafeRetryStrategy(
            new RetryStrategy() {
              public boolean shouldRetry(FetchOptions o, FetchResponse r, int a) {
                throw new IllegalStateException("cannot refresh token");
              }

              public double retryAfter(FetchOptions o, FetchResponse r, int a) {
                return 0;
              }
            });

    assertThatThrownBy(() -> strategy.shouldRetry(plainOptions(), response, 1))
        .isInstanceOf(IllegalStateException.class);
    assertThat(closed).isTrue();
  }

  private static FetchOptions plainOptions() {
    return new FetchOptions.Builder("https://api.box.com/2.0/files/1/content", "GET").build();
  }

  private static RetryStrategy alwaysRetry() {
    return new RetryStrategy() {
      public boolean shouldRetry(FetchOptions o, FetchResponse r, int a) {
        return true;
      }

      public double retryAfter(FetchOptions o, FetchResponse r, int a) {
        return 0;
      }
    };
  }

  private static InputStream trackingStream(AtomicBoolean closed) {
    return new ByteArrayInputStream(new byte[0]) {
      @Override
      public void close() {
        closed.set(true);
      }
    };
  }
}
