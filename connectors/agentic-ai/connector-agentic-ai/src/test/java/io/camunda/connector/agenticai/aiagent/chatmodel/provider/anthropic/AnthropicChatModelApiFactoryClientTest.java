/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CustomEndpointAuthentication.ApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.shared.CustomEndpointAuthentication.NoAuthentication;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link AnthropicChatModelApiFactory}'s {@code custom}-backend and proxy wiring through
 * its public surface ({@link AnthropicChatModelApiFactory#create} + {@link ChatModel#execute})
 * rather than reaching into the private client-building internals: the built {@link ChatModel}
 * issues a real (WireMock-backed) request, and assertions verify what actually went over the wire.
 *
 * <p>The {@code anthropic-api} backend normally targets the production Anthropic base URL; its
 * hidden {@code endpoint} override (settable only by editing the BPMN XML, not via the Modeler UI)
 * is exercised here the same way as the {@code custom} backend's endpoint.
 */
@WireMockTest
class AnthropicChatModelApiFactoryClientTest {

  private static final String MODEL_ID = "claude-sonnet-4-6";

  // Minimal Anthropic Messages streaming (SSE) response: message_start -> one text block -> a
  // message_delta/stop_reason -> message_stop. AnthropicChatModelApi always drives
  // createStreaming(), so a plain buffered JSON body (as a non-streaming stub would return) isn't
  // accepted by the vendor SDK's MessageAccumulator.
  private static final String SSE_RESPONSE_BODY =
      """
      event: message_start
      data: {"type":"message_start","message":{"id":"msg_test","type":"message","role":"assistant","model":"claude-sonnet-4-6","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":0}}}

      event: content_block_start
      data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

      event: content_block_delta
      data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"hi"}}

      event: content_block_stop
      data: {"type":"content_block_stop","index":0}

      event: message_delta
      data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":1}}

      event: message_stop
      data: {"type":"message_stop"}

      """;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AgenticAiHttpProxySupport httpProxySupport = mock(AgenticAiHttpProxySupport.class);

  @BeforeEach
  void setUp() {
    when(httpProxySupport.okHttpProxy(anyString())).thenReturn(Optional.empty());
    stubFor(
        post(urlPathEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(SSE_RESPONSE_BODY)));
  }

  @Test
  void apiBackendHiddenEndpointOverrideRedirectsRequestAndKeepsApiKeyHeader(
      WireMockRuntimeInfo wireMock) {
    executeAgainst(
        new AnthropicApiBackend(
            new AnthropicApiBackend.AnthropicApi(
                "anthropic-api-secret-key", wireMock.getHttpBaseUrl())));

    verify(
        postRequestedFor(urlPathEqualTo("/v1/messages"))
            .withHeader("x-api-key", equalTo("anthropic-api-secret-key")));
  }

  @Test
  void customBackendUsesEndpointAndApiKeyAuthentication(WireMockRuntimeInfo wireMock) {
    executeAgainst(
        new AnthropicCustomBackend(
            new AnthropicCustomBackend.CustomBackend(
                wireMock.getHttpBaseUrl(),
                null,
                null,
                null,
                new ApiKeyAuthentication("custom-secret-key"))));

    verify(
        postRequestedFor(urlPathEqualTo("/v1/messages"))
            .withHeader("x-api-key", equalTo("custom-secret-key")));
  }

  @Test
  void customBackendWithNoAuthenticationSendsNoApiKeyHeader(WireMockRuntimeInfo wireMock) {
    executeAgainst(
        new AnthropicCustomBackend(
            new AnthropicCustomBackend.CustomBackend(
                wireMock.getHttpBaseUrl(), null, null, null, new NoAuthentication())));

    verify(postRequestedFor(urlPathEqualTo("/v1/messages")).withoutHeader("x-api-key"));
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
          new AnthropicCustomBackend(
              new AnthropicCustomBackend.CustomBackend(
                  "http://192.0.2.1:1",
                  null,
                  null,
                  null,
                  new ApiKeyAuthentication("direct-secret-key"))));

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
          new AnthropicCustomBackend(
              new AnthropicCustomBackend.CustomBackend(
                  "http://192.0.2.1:1",
                  null,
                  null,
                  null,
                  new ApiKeyAuthentication("direct-secret-key"))));

      assertThat(fakeProxy.lastProxyAuthorizationHeader())
          .isEqualTo(
              "Basic "
                  + Base64.getEncoder()
                      .encodeToString("proxyuser:proxypass".getBytes(StandardCharsets.UTF_8)));
    }
  }

  private void executeAgainst(AnthropicBackend backend) {
    executeAgainst(httpProxySupport, backend);
  }

  private void executeAgainst(
      AgenticAiHttpProxySupport httpProxySupport, AnthropicBackend backend) {
    final var factory =
        new AnthropicChatModelApiFactory(
            httpProxySupport,
            new AnthropicMessageRequestConverter(new AnthropicContentConverter(objectMapper)),
            new AnthropicMessageResponseConverter(objectMapper));
    final var configuration =
        new AnthropicChatModelConfiguration(
            new AnthropicConnection(backend, new AnthropicModel(MODEL_ID, null), null));

    try (ChatModel chatModel = factory.create(configuration)) {
      chatModel.execute(new ChatRequest(executionContext(configuration), snapshot()));
    }
  }

  private static AgentExecutionContext executionContext(AnthropicChatModelConfiguration model) {
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
   * Minimal hand-rolled HTTP forward proxy used to exercise {@link AnthropicChatModelApiFactory}'s
   * real proxy-application branch end-to-end (rather than mocking {@link
   * AgenticAiHttpProxySupport}, which leaves that branch untested). When credentials are
   * configured, challenges the first request with {@code 407 Proxy Authentication Required} so the
   * vendor SDK's {@code ProxyAuthenticator} actually has to respond, mirroring how a real
   * authenticating proxy behaves.
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
            SSE_RESPONSE_BODY.getBytes(StandardCharsets.UTF_8));
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
