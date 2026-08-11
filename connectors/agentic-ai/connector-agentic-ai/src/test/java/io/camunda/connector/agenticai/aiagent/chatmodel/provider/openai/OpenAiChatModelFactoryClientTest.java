/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

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
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsStreamAssembler;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesStrategy;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesStreamAssembler;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi.CompletionsParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi.ResponsesParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend.OpenAiApiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend.CustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiModel;
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
 * Exercises {@link OpenAiChatModelFactory}'s {@code custom}-backend, header/query-parameter and
 * proxy wiring through its public surface ({@link OpenAiChatModelFactory#create} + {@link
 * ChatModel#execute}) rather than reaching into the private client-building internals: the built
 * {@link ChatModel} issues a real (WireMock-backed) request, and assertions verify what actually
 * went over the wire.
 *
 * <p>The {@code openai-api} backend normally targets the production OpenAI base URL; its hidden
 * {@code endpoint} override is exercised here the same way as the {@code custom} backend's
 * endpoint.
 */
@WireMockTest
class OpenAiChatModelFactoryClientTest {

  private static final String MODEL_ID = "gpt-5.5";

  /**
   * Minimal OpenAI Responses API streaming (SSE) response: a {@code response.created} event
   * carrying an in-progress response, followed by a {@code response.completed} event carrying the
   * final response with one text output item. {@link OpenAiChatModel} always drives the Responses
   * API's {@code createStreaming()}, so a plain buffered JSON body (as a non-streaming stub would
   * return) isn't accepted by the vendor SDK's {@code ResponseAccumulator}.
   */
  private static final String RESPONSES_SSE_RESPONSE_BODY =
      """
      data: {"type":"response.created","sequence_number":0,"response":{"id":"resp_123","object":"response","created_at":0,"model":"gpt-5.5","output":[],"parallel_tool_calls":true,"tool_choice":"auto","tools":[]}}

      data: {"type":"response.completed","sequence_number":1,"response":{"id":"resp_123","object":"response","created_at":0,"model":"gpt-5.5","output":[{"type":"message","id":"msg_1","role":"assistant","status":"completed","content":[{"type":"output_text","text":"hi","annotations":[]}]}],"parallel_tool_calls":true,"tool_choice":"auto","tools":[]}}

      """;

  /**
   * Minimal OpenAI Chat Completions API streaming (SSE) response: a role/content delta chunk
   * followed by a finish_reason chunk, terminated by the {@code [DONE]} sentinel. {@link
   * OpenAiChatModel} always drives the Chat Completions API's {@code createStreaming()}, so a plain
   * buffered JSON body isn't accepted by the vendor SDK's {@code ChatCompletionAccumulator}.
   */
  private static final String COMPLETIONS_SSE_RESPONSE_BODY =
      """
      data: {"id":"chatcmpl_123","object":"chat.completion.chunk","created":0,"model":"gpt-5.5","choices":[{"index":0,"delta":{"role":"assistant","content":"hi"},"finish_reason":null}]}

      data: {"id":"chatcmpl_123","object":"chat.completion.chunk","created":0,"model":"gpt-5.5","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

      data: [DONE]

      """;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AgenticAiHttpProxySupport httpProxySupport = mock(AgenticAiHttpProxySupport.class);

  @BeforeEach
  void setUp() {
    when(httpProxySupport.okHttpProxy(anyString())).thenReturn(Optional.empty());
    stubFor(
        post(urlPathMatching(".*/responses"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(RESPONSES_SSE_RESPONSE_BODY)));
    stubFor(
        post(urlPathMatching(".*/chat/completions"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(COMPLETIONS_SSE_RESPONSE_BODY)));
  }

  @Test
  void appliesOrganizationAndProjectHeadersForOpenAiApiBackend(WireMockRuntimeInfo wireMock) {
    executeAgainst(
        new OpenAiApiBackend(
            new OpenAiApiConnection(
                "openai-api-secret-key",
                "org-test",
                "proj-test",
                wireMock.getHttpBaseUrl(),
                null,
                null,
                null)));

    verify(
        postRequestedFor(urlPathEqualTo("/responses"))
            .withHeader("Authorization", equalTo("Bearer openai-api-secret-key"))
            .withHeader("OpenAI-Organization", equalTo("org-test"))
            .withHeader("OpenAI-Project", equalTo("proj-test")));
  }

  @Test
  void apiBackendAppliesHiddenHeadersAndQueryParameters(WireMockRuntimeInfo wireMock) {
    executeAgainst(
        new OpenAiApiBackend(
            new OpenAiApiConnection(
                "openai-api-secret-key",
                null,
                null,
                wireMock.getHttpBaseUrl(),
                Map.of("X-Custom-Header", "header-value"),
                Map.of("custom-query-param", "query-value"),
                null)));

    verify(
        postRequestedFor(urlPathEqualTo("/responses"))
            .withHeader("X-Custom-Header", equalTo("header-value"))
            .withQueryParam("custom-query-param", equalTo("query-value")));
  }

  @Test
  void usesConfiguredBaseUrlForCustomBackend(WireMockRuntimeInfo wireMock) {
    executeAgainst(
        new OpenAiCustomBackend(
            new CustomBackend(
                wireMock.getHttpBaseUrl(),
                null,
                null,
                null,
                new ApiKeyAuthentication("custom-secret-key"))));

    verify(
        postRequestedFor(urlPathEqualTo("/responses"))
            .withHeader("Authorization", equalTo("Bearer custom-secret-key")));
  }

  @Test
  void customBackendAppliesHeadersAndQueryParameters(WireMockRuntimeInfo wireMock) {
    executeAgainst(
        new OpenAiCustomBackend(
            new CustomBackend(
                wireMock.getHttpBaseUrl(),
                Map.of("X-Custom-Header", "header-value"),
                Map.of("custom-query-param", "query-value"),
                null,
                new NoAuthentication())));

    verify(
        postRequestedFor(urlPathEqualTo("/responses"))
            .withHeader("X-Custom-Header", equalTo("header-value"))
            .withQueryParam("custom-query-param", equalTo("query-value")));
  }

  @Test
  void customBackendWithNoAuthenticationSendsPlaceholderAuthorizationHeader(
      WireMockRuntimeInfo wireMock) {
    // unlike the Anthropic SDK, the OpenAI SDK's client builder requires at least one credential
    // source to build at all (see OpenAiChatModelFactory#NO_AUTH_PLACEHOLDER_API_KEY), so a
    // "none" authentication custom backend still sends a (meaningless, ignored by a real
    // OpenAI-compatible endpoint without authentication) Authorization header.
    executeAgainst(
        new OpenAiCustomBackend(
            new CustomBackend(
                wireMock.getHttpBaseUrl(), null, null, null, new NoAuthentication())));

    verify(
        postRequestedFor(urlPathEqualTo("/responses"))
            .withHeader("Authorization", equalTo("Bearer not-required")));
  }

  @Test
  void selectsResponsesStrategyForResponsesFamily(WireMockRuntimeInfo wireMock) {
    executeAgainst(
        new OpenAiResponsesApi(new ResponsesParameters(null, null, null, null)),
        new OpenAiCustomBackend(
            new CustomBackend(
                wireMock.getHttpBaseUrl(),
                null,
                null,
                null,
                new ApiKeyAuthentication("custom-secret-key"))));

    verify(postRequestedFor(urlPathEqualTo("/responses")));
  }

  @Test
  void selectsCompletionsStrategyForCompletionsFamily(WireMockRuntimeInfo wireMock) {
    executeAgainst(
        new OpenAiCompletionsApi(new CompletionsParameters(null, null, null, null)),
        new OpenAiCustomBackend(
            new CustomBackend(
                wireMock.getHttpBaseUrl(),
                null,
                null,
                null,
                new ApiKeyAuthentication("custom-secret-key"))));

    verify(postRequestedFor(urlPathEqualTo("/chat/completions")));
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
          new OpenAiResponsesApi(new ResponsesParameters(null, null, null, null)),
          new OpenAiCustomBackend(
              new CustomBackend(
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
          new OpenAiResponsesApi(new ResponsesParameters(null, null, null, null)),
          new OpenAiCustomBackend(
              new CustomBackend(
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

  private void executeAgainst(OpenAiBackend backend) {
    executeAgainst(
        httpProxySupport,
        new OpenAiResponsesApi(new ResponsesParameters(null, null, null, null)),
        backend);
  }

  private void executeAgainst(OpenAiChatModelConfiguration.OpenAiApi api, OpenAiBackend backend) {
    executeAgainst(httpProxySupport, api, backend);
  }

  private void executeAgainst(
      AgenticAiHttpProxySupport httpProxySupport,
      OpenAiChatModelConfiguration.OpenAiApi api,
      OpenAiBackend backend) {
    final var contentConverter = new OpenAiContentConverter(objectMapper);
    final var factory =
        new OpenAiChatModelFactory(
            httpProxySupport,
            new OpenAiCompletionsStrategy(
                new OpenAiCompletionsRequestConverter(contentConverter, objectMapper),
                new OpenAiCompletionsResponseConverter(objectMapper),
                OpenAiCompletionsStreamAssembler.accumulating()),
            new OpenAiResponsesStrategy(
                new OpenAiResponsesRequestConverter(contentConverter, objectMapper),
                new OpenAiResponsesResponseConverter(objectMapper),
                OpenAiResponsesStreamAssembler.accumulating()));
    final var configuration =
        new OpenAiChatModelConfiguration(
            new OpenAiChatModelConfiguration.OpenAiConnection(
                api, backend, new OpenAiModel(MODEL_ID), null));

    try (ChatModel chatModel = factory.create(configuration)) {
      chatModel.execute(new ChatRequest(executionContext(configuration), snapshot()));
    }
  }

  private static AgentExecutionContext executionContext(OpenAiChatModelConfiguration model) {
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
   * Minimal hand-rolled HTTP forward proxy used to exercise {@link OpenAiChatModelFactory}'s real
   * proxy-application branch end-to-end (rather than mocking {@link AgenticAiHttpProxySupport},
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
            RESPONSES_SSE_RESPONSE_BODY.getBytes(StandardCharsets.UTF_8));
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
