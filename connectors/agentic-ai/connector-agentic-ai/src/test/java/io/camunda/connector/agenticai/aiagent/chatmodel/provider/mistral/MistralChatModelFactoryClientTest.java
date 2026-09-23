/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStreamAssembler;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralBackend.MistralApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralBackend.MistralApiBackend.MistralApiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.MistralChatModelConfiguration.MistralModel;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.ApiProperties;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.AzureProperties;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties.AzureProperties.CredentialCacheProperties;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link MistralChatModelFactory}'s client-construction and proxy wiring through its
 * public surface ({@link MistralChatModelFactory#create} + {@link ChatModel#execute}), the same way
 * {@code OpenAiChatModelFactoryClientTest} does for the OpenAI provider: the built {@link
 * ChatModel} issues a real (WireMock-backed) request, and assertions verify what actually went over
 * the wire. HTTP proxy support is mandatory for this provider (camunda/connectors#8052), so the
 * proxy tests below are not optional coverage.
 */
@WireMockTest
class MistralChatModelFactoryClientTest {

  private static final String MODEL_ID = "mistral-medium-latest";

  /**
   * Minimal Mistral/OpenAI-shaped Chat Completions streaming (SSE) response: a role/content delta
   * chunk followed by a finish_reason chunk, terminated by the {@code [DONE]} sentinel -- {@link
   * MistralChatModel} always drives {@code createStreaming()}, so a plain buffered JSON body isn't
   * accepted by the vendor SDK's {@code ChatCompletionAccumulator}.
   */
  private static final String COMPLETIONS_SSE_RESPONSE_BODY =
      """
      data: {"id":"chatcmpl_123","object":"chat.completion.chunk","created":0,"model":"mistral-medium-latest","choices":[{"index":0,"delta":{"role":"assistant","content":"hi"},"finish_reason":null}]}

      data: {"id":"chatcmpl_123","object":"chat.completion.chunk","created":0,"model":"mistral-medium-latest","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

      data: [DONE]

      """;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AgenticAiHttpProxySupport httpProxySupport = mock(AgenticAiHttpProxySupport.class);
  private final ChatModelProperties chatModelProperties =
      new ChatModelProperties(
          new ApiProperties(Duration.ofMinutes(3)),
          new AzureProperties(new CredentialCacheProperties(true, 100L, Duration.ofMinutes(10))));

  @BeforeEach
  void setUp() {
    when(httpProxySupport.okHttpProxy(anyString())).thenReturn(Optional.empty());
    stubFor(
        post(urlPathMatching(".*/chat/completions"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(COMPLETIONS_SSE_RESPONSE_BODY)));
  }

  @Test
  void appliesApiKeyAsBearerAuthorizationHeader(WireMockRuntimeInfo wireMock) {
    executeAgainst(
        new MistralApiBackend(
            new MistralApiConnection(
                "mistral-secret-key", wireMock.getHttpBaseUrl(), null, null, null)));

    verify(
        postRequestedFor(urlPathEqualTo("/chat/completions"))
            .withHeader("Authorization", equalTo("Bearer mistral-secret-key")));
  }

  @Test
  void usesConfiguredEndpointOverride(WireMockRuntimeInfo wireMock) {
    executeAgainst(
        new MistralApiBackend(
            new MistralApiConnection(
                "mistral-secret-key", wireMock.getHttpBaseUrl(), null, null, null)));

    verify(postRequestedFor(urlPathEqualTo("/chat/completions")));
  }

  @Test
  void appliesHiddenHeadersAndQueryParameters(WireMockRuntimeInfo wireMock) {
    executeAgainst(
        new MistralApiBackend(
            new MistralApiConnection(
                "mistral-secret-key",
                wireMock.getHttpBaseUrl(),
                Map.of("X-Custom-Header", "header-value"),
                Map.of("custom-query-param", "query-value"),
                null)));

    verify(
        postRequestedFor(urlPathEqualTo("/chat/completions"))
            .withHeader("X-Custom-Header", equalTo("header-value"))
            .withQueryParam("custom-query-param", equalTo("query-value")));
  }

  @Test
  void appliesConfiguredProxyToBuiltClient() throws Exception {
    try (var fakeProxy = new FakeProxyServer(null, null)) {
      final var realHttpProxySupport =
          new AgenticAiHttpProxySupport(fakeProxy.toProxyConfiguration());

      // the target host is a non-routable address (RFC 5737 TEST-NET-1): reaching it directly
      // would hang/fail, so a successful response here proves the request actually went through
      // the configured proxy rather than straight to the (unreachable) target.
      executeAgainst(
          realHttpProxySupport,
          new MistralApiBackend(
              new MistralApiConnection(
                  "mistral-secret-key", "http://192.0.2.1:1", null, null, null)));

      assertThat(fakeProxy.lastRequestLine()).contains("192.0.2.1");
    }
  }

  @Test
  void appliesProxyCredentialsViaProxyAuthenticator() throws Exception {
    try (var fakeProxy = new FakeProxyServer("proxyuser", "proxypass")) {
      final var realHttpProxySupport =
          new AgenticAiHttpProxySupport(fakeProxy.toProxyConfiguration());

      executeAgainst(
          realHttpProxySupport,
          new MistralApiBackend(
              new MistralApiConnection(
                  "mistral-secret-key", "http://192.0.2.1:1", null, null, null)));

      assertThat(fakeProxy.lastProxyAuthorizationHeader())
          .isEqualTo(
              "Basic "
                  + Base64.getEncoder()
                      .encodeToString("proxyuser:proxypass".getBytes(StandardCharsets.UTF_8)));
    }
  }

  private void executeAgainst(MistralBackend backend) {
    executeAgainst(httpProxySupport, backend);
  }

  private void executeAgainst(AgenticAiHttpProxySupport httpProxySupport, MistralBackend backend) {
    final var contentConverter = new OpenAiContentConverter(objectMapper);
    final var factory =
        new MistralChatModelFactory(
            chatModelProperties,
            httpProxySupport,
            new OpenAiCompletionsRequestConverter(contentConverter, objectMapper),
            new OpenAiCompletionsResponseConverter(objectMapper),
            OpenAiCompletionsStreamAssembler.accumulating());
    final var configuration =
        new MistralChatModelConfiguration(
            new MistralConnection(backend, new MistralModel(MODEL_ID), null, null));

    try (ChatModel chatModel = factory.create(configuration)) {
      chatModel.execute(new ChatRequest(executionContext(configuration), snapshot()));
    }
  }

  private static AgentExecutionContext executionContext(MistralChatModelConfiguration model) {
    final var agentConfiguration =
        new AgentConfiguration(
            model,
            new SystemPromptConfiguration("system prompt"),
            new UserPromptConfiguration("user prompt", null),
            null,
            null,
            null,
            null);

    final var executionContext = mock(AgentExecutionContext.class);
    when(executionContext.configuration()).thenReturn(agentConfiguration);
    return executionContext;
  }

  private static ConversationSnapshot snapshot() {
    return new ConversationSnapshot(List.of(), List.of());
  }

  /**
   * Minimal hand-rolled HTTP forward proxy used to exercise {@link MistralChatModelFactory}'s real
   * proxy-application branch end-to-end, mirroring {@code OpenAiChatModelFactoryClientTest}'s copy
   * of the same helper. When credentials are configured, challenges the first request with {@code
   * 407 Proxy Authentication Required} so the vendor SDK's {@code ProxyAuthenticator} actually has
   * to respond, mirroring how a real authenticating proxy behaves.
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
      // Reads directly off the socket's InputStream rather than through a BufferedReader /
      // InputStreamReader: closing that wrapper closes the underlying socket (per Socket's
      // documented stream-close coupling), which would break the response write below.
      final InputStream in = socket.getInputStream();
      String requestLine = readLine(in);
      if (requestLine == null) {
        return;
      }
      lastRequestLine = requestLine;

      int contentLength = 0;
      String proxyAuthorizationHeader = null;
      String line;
      while ((line = readLine(in)) != null && !line.isEmpty()) {
        if (line.regionMatches(true, 0, "Content-Length:", 0, "Content-Length:".length())) {
          try {
            contentLength = Integer.parseInt(line.substring("Content-Length:".length()).trim());
          } catch (NumberFormatException e) {
            contentLength = 0;
          }
        }
        if (line.regionMatches(
            true, 0, "Proxy-Authorization:", 0, "Proxy-Authorization:".length())) {
          proxyAuthorizationHeader = line.substring("Proxy-Authorization:".length()).trim();
        }
      }

      int read = 0;
      while (read < contentLength) {
        if (in.read() < 0) {
          break;
        }
        read++;
      }

      boolean authRequired = username != null;
      boolean authSatisfied = !authRequired || proxyAuthorizationHeader != null;
      if (authSatisfied) {
        lastProxyAuthorizationHeader = proxyAuthorizationHeader;
        writeResponse(
            socket,
            200,
            "OK",
            Map.of("Content-Type", "text/event-stream"),
            COMPLETIONS_SSE_RESPONSE_BODY.getBytes(StandardCharsets.UTF_8));
      } else {
        writeResponse(
            socket,
            407,
            "Proxy Authentication Required",
            Map.of("Proxy-Authenticate", "Basic realm=\"fake-proxy\""),
            new byte[0]);
      }
    }

    /** Reads one CRLF- or LF-terminated line as US-ASCII; returns null at end of stream. */
    private static @Nullable String readLine(InputStream in) throws IOException {
      var line = new StringBuilder();
      int c;
      while ((c = in.read()) != -1 && c != '\n') {
        if (c != '\r') {
          line.append((char) c);
        }
      }
      if (c == -1 && line.isEmpty()) {
        return null;
      }
      return line.toString();
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
