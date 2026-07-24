/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCompatibleBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CompatibleAuthentication.CompatibleApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CompatibleAuthentication.CompatibleNoAuthentication;
import io.camunda.connector.agenticai.aiagent.transport.HttpTransportSupport;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link AnthropicChatModelApiFactory#buildClient} at the wire level: the built {@link
 * AnthropicClient} issues a real (WireMock-backed) request, and assertions verify what actually
 * went over the wire rather than reflecting into SDK internals.
 */
@WireMockTest
class AnthropicChatModelApiFactoryClientTest {

  private static final String SAMPLE_MESSAGE_RESPONSE =
      """
      {
        "id": "msg_test",
        "model": "claude-sonnet-4-6",
        "role": "assistant",
        "type": "message",
        "content": [{"type": "text", "text": "hi"}],
        "stop_reason": "end_turn",
        "usage": {"input_tokens": 1, "output_tokens": 1}
      }
      """;

  private final HttpTransportSupport transport = mock(HttpTransportSupport.class);

  @BeforeEach
  void setUp() {
    when(transport.okHttpProxy(anyString())).thenReturn(Optional.empty());
    stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(okJson(SAMPLE_MESSAGE_RESPONSE)));
  }

  @Test
  void directBackendSendsConfiguredApiKey(WireMockRuntimeInfo wireMock) {
    var backend = new AnthropicApiBackend("direct-secret-key");
    AnthropicClient client = AnthropicChatModelApiFactory.buildClient(backend, null, transport);

    // the direct backend always targets the production Anthropic base URL; redirect this one
    // instance to the WireMock server while keeping its resolved api key credential intact.
    AnthropicClient redirected =
        client.withOptions(options -> options.baseUrl(wireMock.getHttpBaseUrl()));
    redirected.messages().create(minimalMessageParams());

    verify(
        postRequestedFor(urlPathEqualTo("/v1/messages"))
            .withHeader("x-api-key", equalTo("direct-secret-key")));
  }

  @Test
  void compatibleBackendUsesEndpointAndApiKeyAuthentication(WireMockRuntimeInfo wireMock) {
    var backend =
        new AnthropicCompatibleBackend(
            wireMock.getHttpBaseUrl(),
            null,
            null,
            null,
            new CompatibleApiKeyAuthentication("compatible-secret-key"));

    var client = AnthropicChatModelApiFactory.buildClient(backend, null, transport);
    client.messages().create(minimalMessageParams());

    verify(
        postRequestedFor(urlPathEqualTo("/v1/messages"))
            .withHeader("x-api-key", equalTo("compatible-secret-key")));
  }

  @Test
  void compatibleBackendWithNoAuthenticationSendsNoApiKeyHeader(WireMockRuntimeInfo wireMock) {
    var backend =
        new AnthropicCompatibleBackend(
            wireMock.getHttpBaseUrl(), null, null, null, new CompatibleNoAuthentication());

    var client = AnthropicChatModelApiFactory.buildClient(backend, null, transport);
    client.messages().create(minimalMessageParams());

    verify(postRequestedFor(urlPathEqualTo("/v1/messages")).withoutHeader("x-api-key"));
  }

  @Test
  void appliesConfiguredProxyToBuiltClient() throws Exception {
    try (var fakeProxy = new FakeProxyServer(null, null)) {
      var realTransport = new HttpTransportSupport(fakeProxy.toProxyConfiguration());

      // the target host is a non-routable address (RFC 5737 TEST-NET-1): reaching it directly
      // would hang/fail, so a successful response here proves the request actually went through
      // the configured proxy rather than straight to the (unreachable) target.
      var backend =
          new AnthropicCompatibleBackend(
              "http://192.0.2.1:1",
              null,
              null,
              null,
              new CompatibleApiKeyAuthentication("direct-secret-key"));

      var client = AnthropicChatModelApiFactory.buildClient(backend, null, realTransport);
      client.messages().create(minimalMessageParams());

      assertThat(fakeProxy.lastRequestLine()).contains("192.0.2.1");
    }
  }

  @Test
  void appliesProxyCredentialsViaProxyAuthenticator() throws Exception {
    try (var fakeProxy = new FakeProxyServer("proxyuser", "proxypass")) {
      var realTransport = new HttpTransportSupport(fakeProxy.toProxyConfiguration());

      var backend =
          new AnthropicCompatibleBackend(
              "http://192.0.2.1:1",
              null,
              null,
              null,
              new CompatibleApiKeyAuthentication("direct-secret-key"));

      var client = AnthropicChatModelApiFactory.buildClient(backend, null, realTransport);
      client.messages().create(minimalMessageParams());

      assertThat(fakeProxy.lastProxyAuthorizationHeader())
          .isEqualTo(
              "Basic "
                  + Base64.getEncoder()
                      .encodeToString("proxyuser:proxypass".getBytes(StandardCharsets.UTF_8)));
    }
  }

  private static MessageCreateParams minimalMessageParams() {
    return MessageCreateParams.builder()
        .model("claude-sonnet-4-6")
        .maxTokens(16)
        .addUserMessage("hi")
        .build();
  }

  /**
   * Minimal hand-rolled HTTP forward proxy used to exercise {@link AnthropicChatModelApiFactory}'s
   * real proxy-application branch end-to-end (rather than mocking {@link HttpTransportSupport},
   * which leaves that branch untested). When credentials are configured, challenges the first
   * request with {@code 407 Proxy Authentication Required} so the vendor SDK's {@code
   * ProxyAuthenticator} actually has to respond, mirroring how a real authenticating proxy behaves.
   */
  private static final class FakeProxyServer implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final Thread acceptThread;
    private final String username;
    private final String password;
    private volatile String lastRequestLine;
    private volatile String lastProxyAuthorizationHeader;

    FakeProxyServer(String username, String password) throws IOException {
      this.username = username;
      this.password = password;
      this.serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
      this.acceptThread = new Thread(this::acceptLoop, "fake-proxy-accept");
      this.acceptThread.setDaemon(true);
      this.acceptThread.start();
    }

    String lastRequestLine() {
      return lastRequestLine;
    }

    String lastProxyAuthorizationHeader() {
      return lastProxyAuthorizationHeader;
    }

    ProxyConfiguration toProxyConfiguration() {
      return scheme ->
          Optional.of(
              new ProxyConfiguration.ProxyDetails(
                  scheme, "127.0.0.1", serverSocket.getLocalPort(), username, password));
    }

    private void acceptLoop() {
      while (!serverSocket.isClosed()) {
        try (Socket socket = serverSocket.accept()) {
          handle(socket);
        } catch (IOException e) {
          // server socket closed (test cleanup) or connection reset -- exit the loop
          return;
        }
      }
    }

    private void handle(Socket socket) throws IOException {
      var reader =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
      String requestLine = reader.readLine();
      if (requestLine == null) {
        return;
      }
      lastRequestLine = requestLine;

      int contentLength = 0;
      String proxyAuthorizationHeader = null;
      String line;
      while ((line = reader.readLine()) != null && !line.isEmpty()) {
        if (line.regionMatches(true, 0, "Content-Length:", 0, "Content-Length:".length())) {
          contentLength = Integer.parseInt(line.substring("Content-Length:".length()).trim());
        }
        if (line.regionMatches(
            true, 0, "Proxy-Authorization:", 0, "Proxy-Authorization:".length())) {
          proxyAuthorizationHeader = line.substring("Proxy-Authorization:".length()).trim();
        }
      }

      char[] body = new char[contentLength];
      int read = 0;
      while (read < contentLength) {
        int r = reader.read(body, read, contentLength - read);
        if (r < 0) {
          break;
        }
        read += r;
      }

      boolean authRequired = username != null;
      boolean authSatisfied = !authRequired || proxyAuthorizationHeader != null;
      if (authSatisfied) {
        lastProxyAuthorizationHeader = proxyAuthorizationHeader;
        writeResponse(
            socket,
            200,
            "OK",
            Map.of("Content-Type", "application/json"),
            SAMPLE_MESSAGE_RESPONSE.getBytes(StandardCharsets.UTF_8));
      } else {
        writeResponse(
            socket,
            407,
            "Proxy Authentication Required",
            Map.of("Proxy-Authenticate", "Basic realm=\"fake-proxy\""),
            new byte[0]);
      }
    }

    private static void writeResponse(
        Socket socket, int status, String reason, Map<String, String> headers, byte[] body)
        throws IOException {
      var out = socket.getOutputStream();
      var responseHeaders = new StringBuilder();
      responseHeaders.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");
      headers.forEach(
          (name, value) -> responseHeaders.append(name).append(": ").append(value).append("\r\n"));
      responseHeaders.append("Content-Length: ").append(body.length).append("\r\n");
      responseHeaders.append("Connection: close\r\n\r\n");
      out.write(responseHeaders.toString().getBytes(StandardCharsets.US_ASCII));
      out.write(body);
      out.flush();
    }

    @Override
    public void close() throws IOException {
      serverSocket.close();
    }
  }
}
