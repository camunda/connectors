/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.core.http.HttpMethod;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpRequestBody;
import com.anthropic.core.http.HttpResponse;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@WireMockTest
class JdkAnthropicHttpClientTest {

  private JdkAnthropicHttpClient client;

  @BeforeEach
  void setUp() {
    java.net.http.HttpClient jdkClient =
        java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    client = new JdkAnthropicHttpClient(jdkClient);
  }

  @Test
  void posts_json_body_and_returns_response_body(WireMockRuntimeInfo wm) throws Exception {
    stubFor(
        post(urlEqualTo("/anthropic/v1/messages"))
            .withHeader("X-Test-Header", equalTo("value"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"ok\":true}")));

    byte[] bodyBytes = "{\"msg\":\"hi\"}".getBytes(StandardCharsets.UTF_8);
    HttpRequest request =
        HttpRequest.builder()
            .method(HttpMethod.POST)
            .baseUrl("http://localhost:" + wm.getHttpPort())
            .addPathSegments("anthropic", "v1", "messages")
            .putHeader("X-Test-Header", "value")
            .body(jsonBody(bodyBytes))
            .build();

    try (HttpResponse response = client.execute(request)) {
      assertThat(response.statusCode()).isEqualTo(200);
      String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
      assertThat(body).isEqualTo("{\"ok\":true}");
    }

    verify(
        postRequestedFor(urlEqualTo("/anthropic/v1/messages"))
            .withHeader("X-Test-Header", equalTo("value"))
            .withRequestBody(equalToJson("{\"msg\":\"hi\"}")));
  }

  @Test
  void propagates_non_2xx_as_http_response_not_exception(WireMockRuntimeInfo wm) throws Exception {
    stubFor(
        post(urlEqualTo("/err"))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"too fast\"}}")));

    HttpRequest request =
        HttpRequest.builder()
            .method(HttpMethod.POST)
            .baseUrl("http://localhost:" + wm.getHttpPort())
            .addPathSegment("err")
            .build();

    try (HttpResponse response = client.execute(request)) {
      assertThat(response.statusCode()).isEqualTo(429);
      assertThat(new String(response.body().readAllBytes(), StandardCharsets.UTF_8))
          .contains("rate_limit_error");
    }
  }

  @Test
  void async_execute_round_trips(WireMockRuntimeInfo wm) throws Exception {
    stubFor(post(urlEqualTo("/async")).willReturn(aResponse().withStatus(200).withBody("ok")));

    HttpRequest request =
        HttpRequest.builder()
            .method(HttpMethod.POST)
            .baseUrl("http://localhost:" + wm.getHttpPort())
            .addPathSegment("async")
            .build();

    CompletableFuture<HttpResponse> future = client.executeAsync(request);
    try (HttpResponse response = future.get(5, TimeUnit.SECONDS)) {
      assertThat(response.statusCode()).isEqualTo(200);
    }
  }

  @Test
  void get_request_without_body_works(WireMockRuntimeInfo wm) throws Exception {
    stubFor(get(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200).withBody("pong")));

    HttpRequest request =
        HttpRequest.builder()
            .method(HttpMethod.GET)
            .baseUrl("http://localhost:" + wm.getHttpPort())
            .addPathSegment("ping")
            .build();

    try (HttpResponse response = client.execute(request)) {
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(new String(response.body().readAllBytes(), StandardCharsets.UTF_8))
          .isEqualTo("pong");
    }

    verify(getRequestedFor(urlEqualTo("/ping")));
  }

  @Test
  void close_does_not_throw() {
    client.close();
  }

  private static HttpRequestBody jsonBody(byte[] bytes) {
    return new HttpRequestBody() {
      @Override
      public void writeTo(OutputStream out) throws IOException {
        out.write(bytes);
      }

      @Override
      public String contentType() {
        return "application/json";
      }

      @Override
      public long contentLength() {
        return bytes.length;
      }

      @Override
      public boolean repeatable() {
        return true;
      }

      @Override
      public void close() {}
    };
  }
}
