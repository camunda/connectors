/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.a2a.client.http.A2AHttpClient;
import io.a2a.client.http.ServerSentEvent;
import io.a2a.common.A2AErrorMessages;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CustomJdkA2AHttpClientTest {

  private static final String SSE_PATH = "/sse";
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  @RegisterExtension
  static WireMockExtension server =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @RegisterExtension
  static WireMockExtension proxy =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Test
  void defaultConstructorCreatesClient() {
    var client = new CustomJdkA2AHttpClient();
    assertThat(client.createGet()).isNotNull();
    assertThat(client.createPost()).isNotNull();
    assertThat(client.createDelete()).isNotNull();
  }

  @Test
  void customHttpClientConstructorCreatesClient() {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    var client = new CustomJdkA2AHttpClient(httpClient);
    assertThat(client.createGet()).isNotNull();
    assertThat(client.createPost()).isNotNull();
    assertThat(client.createDelete()).isNotNull();
  }

  @Test
  void sseDeliversEachEventAndCompletes() throws Exception {
    stubSseGet("data: first\n\ndata: second\n\n");
    var recorder = new SseRecorder();

    recorder.subscribe(client().createGet().url(serverUrl(SSE_PATH)));

    recorder.awaitCompleted();
    assertThat(recorder.events)
        .containsExactly(new ServerSentEvent("first"), new ServerSentEvent("second"));
    assertThat(recorder.errors).isEmpty();
    server.verify(
        getRequestedFor(urlPathEqualTo(SSE_PATH))
            .withHeader("Accept", equalTo("text/event-stream")));
  }

  @Test
  void sseDispatchesTrailingEventWithoutBlankLine() throws Exception {
    stubSseGet("data: first\n\ndata: last");
    var recorder = new SseRecorder();

    recorder.subscribe(client().createGet().url(serverUrl(SSE_PATH)));

    recorder.awaitCompleted();
    assertThat(recorder.events)
        .containsExactly(new ServerSentEvent("first"), new ServerSentEvent("last"));
  }

  @Test
  void sseParsesEventTypeIdRetryAndMultiLineData() throws Exception {
    stubSseGet(
        ": heartbeat comment\n"
            + "event: status-update\n"
            + "id: 42\n"
            + "retry: 1500\n"
            + "data: line1\n"
            + "data: line2\n"
            + "\n");
    var recorder = new SseRecorder();

    recorder.subscribe(client().createGet().url(serverUrl(SSE_PATH)));

    recorder.awaitCompleted();
    assertThat(recorder.events)
        .containsExactly(new ServerSentEvent("line1\nline2", "status-update", "42", 1500L));
  }

  @Test
  void ssePostSendsBodyAndAcceptHeader() throws Exception {
    server.stubFor(
        post(urlPathEqualTo(SSE_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody("data: {\"result\":1}\n\n")));
    var recorder = new SseRecorder();

    recorder.subscribe(
        client().createPost().url(serverUrl(SSE_PATH)).body("{\"question\":\"hi\"}"));

    recorder.awaitCompleted();
    assertThat(recorder.events).containsExactly(new ServerSentEvent("{\"result\":1}"));
    server.verify(
        postRequestedFor(urlPathEqualTo(SSE_PATH))
            .withHeader("Accept", equalTo("text/event-stream"))
            .withRequestBody(equalTo("{\"question\":\"hi\"}")));
  }

  @ParameterizedTest
  @CsvSource({"401, AUTHENTICATION_FAILED", "403, AUTHORIZATION_FAILED"})
  void sseReportsAuthErrorsToErrorConsumer(int status, String expectedMessageConstant)
      throws Exception {
    server.stubFor(get(urlPathEqualTo(SSE_PATH)).willReturn(aResponse().withStatus(status)));
    var recorder = new SseRecorder();

    recorder.subscribe(client().createGet().url(serverUrl(SSE_PATH)));

    await().atMost(TIMEOUT).until(() -> !recorder.errors.isEmpty());
    assertThat(recorder.errors)
        .singleElement()
        .asInstanceOf(InstanceOfAssertFactories.THROWABLE)
        .isInstanceOf(IOException.class)
        .hasMessage(authMessage(expectedMessageConstant));
    assertThat(recorder.events).isEmpty();
    assertThat(recorder.completed).isFalse();
  }

  @Test
  void sseReportsOtherErrorStatusToErrorConsumer() throws Exception {
    server.stubFor(get(urlPathEqualTo(SSE_PATH)).willReturn(aResponse().withStatus(500)));
    var recorder = new SseRecorder();

    recorder.subscribe(client().createGet().url(serverUrl(SSE_PATH)));

    await().atMost(TIMEOUT).until(() -> !recorder.errors.isEmpty());
    assertThat(recorder.errors)
        .singleElement()
        .asInstanceOf(InstanceOfAssertFactories.THROWABLE)
        .isInstanceOf(IOException.class)
        .hasMessage("Request failed with status 500");
    assertThat(recorder.events).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"401, AUTHENTICATION_FAILED", "403, AUTHORIZATION_FAILED"})
  void synchronousCallsThrowOnAuthErrors(int status, String expectedMessageConstant) {
    server.stubFor(get(urlPathEqualTo("/resource")).willReturn(aResponse().withStatus(status)));
    server.stubFor(post(urlPathEqualTo("/resource")).willReturn(aResponse().withStatus(status)));
    server.stubFor(delete(urlPathEqualTo("/resource")).willReturn(aResponse().withStatus(status)));
    var expected = authMessage(expectedMessageConstant);
    var client = client();

    assertThatThrownBy(() -> client.createGet().url(serverUrl("/resource")).get())
        .isInstanceOf(IOException.class)
        .hasMessage(expected);
    assertThatThrownBy(() -> client.createPost().url(serverUrl("/resource")).body("{}").post())
        .isInstanceOf(IOException.class)
        .hasMessage(expected);
    assertThatThrownBy(() -> client.createDelete().url(serverUrl("/resource")).delete())
        .isInstanceOf(IOException.class)
        .hasMessage(expected);
  }

  @Test
  void synchronousGetReturnsBodyAndStatus() throws Exception {
    server.stubFor(
        get(urlPathEqualTo("/resource")).willReturn(aResponse().withStatus(200).withBody("hello")));

    var response = client().createGet().url(serverUrl("/resource")).get();

    assertThat(response.success()).isTrue();
    assertThat(response.status()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("hello");
  }

  @Test
  void usesTheProvidedHttpClientsProxy() throws Exception {
    // The target host does not resolve; the request can only succeed if it is sent to the proxy.
    String unresolvableTarget = "http://a2a-agent.invalid";
    proxy.stubFor(
        get(urlPathEqualTo("/resource"))
            .willReturn(aResponse().withStatus(200).withBody("proxied")));
    proxy.stubFor(
        get(urlPathEqualTo(SSE_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody("data: via-proxy\n\n")));
    var proxiedClient =
        new CustomJdkA2AHttpClient(
            HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(ProxySelector.of(new InetSocketAddress("localhost", proxy.getPort())))
                .build());

    var response = proxiedClient.createGet().url(unresolvableTarget + "/resource").get();
    var recorder = new SseRecorder();
    recorder.subscribe(proxiedClient.createGet().url(unresolvableTarget + SSE_PATH));
    recorder.awaitCompleted();

    assertThat(response.body()).isEqualTo("proxied");
    assertThat(recorder.events).containsExactly(new ServerSentEvent("via-proxy"));
    proxy.verify(getRequestedFor(urlPathEqualTo("/resource")));
    proxy.verify(getRequestedFor(urlPathEqualTo(SSE_PATH)));
  }

  private static A2AHttpClient client() {
    // HTTP/1.1: WireMock serves plain HTTP and the tests exercise the client logic, not the
    // protocol negotiation.
    return new CustomJdkA2AHttpClient(
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build());
  }

  private static String serverUrl(String path) {
    return "http://localhost:" + server.getPort() + path;
  }

  private static void stubSseGet(String body) {
    server.stubFor(
        get(urlPathEqualTo(SSE_PATH))
            .willReturn(
                aResponse().withHeader("Content-Type", "text/event-stream").withBody(body)));
  }

  private static String authMessage(String constantName) {
    return switch (constantName) {
      case "AUTHENTICATION_FAILED" -> A2AErrorMessages.AUTHENTICATION_FAILED;
      case "AUTHORIZATION_FAILED" -> A2AErrorMessages.AUTHORIZATION_FAILED;
      default -> throw new IllegalArgumentException(constantName);
    };
  }

  /** Collects the callbacks of an SSE request. */
  private static class SseRecorder {
    final List<ServerSentEvent> events = new CopyOnWriteArrayList<>();
    final List<Throwable> errors = new CopyOnWriteArrayList<>();
    volatile boolean completed;

    void subscribe(A2AHttpClient.GetBuilder builder) throws Exception {
      builder.getAsyncSSE(events::add, errors::add, () -> completed = true);
    }

    void subscribe(A2AHttpClient.PostBuilder builder) throws Exception {
      builder.postAsyncSSE(events::add, errors::add, () -> completed = true);
    }

    void awaitCompleted() {
      await().atMost(TIMEOUT).until(() -> completed || !errors.isEmpty());
      assertThat(errors).isEmpty();
      assertThat(completed).isTrue();
    }
  }
}
