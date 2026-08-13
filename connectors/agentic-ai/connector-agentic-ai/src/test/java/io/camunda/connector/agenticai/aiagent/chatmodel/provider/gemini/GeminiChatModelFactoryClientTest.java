/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend.GoogleGeminiApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel;
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
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link GeminiChatModelFactory}'s endpoint and <strong>proxy</strong> wiring through its
 * public surface ({@link GeminiChatModelFactory#create} + {@link ChatModel#execute}) rather than
 * reaching into the private client-building internals: the built {@link ChatModel} issues a real
 * HTTP request, and assertions verify what actually went over the wire.
 *
 * <p>This is the deliberate counterpart to {@link GeminiChatModelFactoryTest}, which asserts only
 * that we <em>configure</em> {@code ClientOptions.proxyOptions} correctly (host/port/credentials,
 * via a mocked {@link AgenticAiHttpProxySupport}). That leaves the real question untested: whether
 * the google-genai SDK actually <em>honors</em> those options on the wire. Mirrors {@code
 * AnthropicChatModelFactoryClientTest} for the sibling native provider, which exists for exactly
 * this reason.
 *
 * <p>The SDK applies {@code ProxyOptions} to its OkHttp client ({@code
 * ApiClient#applyProxyOptions}: {@code builder.proxy(...)} plus a {@code proxyAuthenticator} when
 * credentials are present), so both the credential-less and the authenticating proxy paths are
 * covered below.
 */
@WireMockTest
class GeminiChatModelFactoryClientTest {

  private static final String MODEL_ID = "gemini-3-pro-preview";
  private static final String API_KEY = "gemini-api-secret-key";

  /**
   * Target used by the proxy tests: a non-routable address (RFC 5737 TEST-NET-1). Reaching it
   * directly would hang or fail, so a successful response proves the request actually went through
   * the configured proxy rather than straight to the (unreachable) target.
   */
  private static final String UNREACHABLE_ENDPOINT = "http://192.0.2.1:1";

  /**
   * Minimal Gemini streaming (SSE) response: a single {@code data:} line carrying one complete
   * {@link GenerateContentResponse}. {@code GeminiChatModel} always drives {@code
   * generateContentStream}, so a plain buffered JSON body would not be read as a stream; and the
   * stream assembler rejects a stream with no chunks at all.
   *
   * <p>Built through the SDK's own builders and {@code toJson()} so every field name comes from the
   * SDK's {@code @JsonProperty} annotations rather than a hand-written literal.
   */
  private static final String SSE_RESPONSE_BODY =
      "data: "
          + GenerateContentResponse.builder()
              .candidates(
                  List.of(
                      Candidate.builder()
                          .index(0)
                          .content(
                              Content.builder()
                                  .role("model")
                                  .parts(List.of(Part.builder().text("hi").build()))
                                  .build())
                          .finishReason(FinishReason.Known.STOP)
                          .build()))
              .usageMetadata(
                  GenerateContentResponseUsageMetadata.builder()
                      .promptTokenCount(1)
                      .candidatesTokenCount(1)
                      .totalTokenCount(2)
                      .build())
              .build()
              .toJson()
          + "\n\n";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void hiddenEndpointOverrideRedirectsRequestAndKeepsApiKeyHeaderAndSseQueryParam(
      WireMockRuntimeInfo wireMock) {
    stubStreamGenerateContent();

    executeAgainst(noProxy(), wireMock.getHttpBaseUrl());

    // Baseline for the proxy tests below: proves the built client really issues the request, so the
    // fake-proxy assertions are meaningful by contrast rather than vacuous.
    verify(
        postRequestedFor(urlPathMatching("/v1beta/models/.+:streamGenerateContent"))
            .withHeader("x-goog-api-key", equalTo(API_KEY))
            // The SDK requests SSE framing explicitly; without it Gemini would return a JSON array.
            .withQueryParam("alt", equalTo("sse")));
  }

  @Test
  void appliesConfiguredProxyToBuiltClient() throws Exception {
    try (var fakeProxy = new FakeProxyServer(null, null)) {
      executeAgainst(
          new AgenticAiHttpProxySupport(fakeProxy.toProxyConfiguration()), UNREACHABLE_ENDPOINT);

      assertThat(fakeProxy.lastRequestLine()).contains("192.0.2.1");
    }
  }

  @Test
  void appliesProxyCredentialsViaProxyAuthenticator() throws Exception {
    try (var fakeProxy = new FakeProxyServer("proxyuser", "proxypass")) {
      executeAgainst(
          new AgenticAiHttpProxySupport(fakeProxy.toProxyConfiguration()), UNREACHABLE_ENDPOINT);

      assertThat(fakeProxy.lastProxyAuthorizationHeader())
          .isEqualTo(
              "Basic "
                  + Base64.getEncoder()
                      .encodeToString("proxyuser:proxypass".getBytes(StandardCharsets.UTF_8)));
    }
  }

  private static void stubStreamGenerateContent() {
    stubFor(
        post(urlPathMatching(".*/v1beta/models/.+:streamGenerateContent"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(SSE_RESPONSE_BODY)));
  }

  private static AgenticAiHttpProxySupport noProxy() {
    final var httpProxySupport = mock(AgenticAiHttpProxySupport.class);
    final var proxyConfiguration = mock(ProxyConfiguration.class);
    when(httpProxySupport.getProxyConfiguration()).thenReturn(proxyConfiguration);
    when(proxyConfiguration.getProxyDetails(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(Optional.empty());
    return httpProxySupport;
  }

  private void executeAgainst(AgenticAiHttpProxySupport httpProxySupport, String endpoint) {
    final var factory =
        new GeminiChatModelFactory(
            httpProxySupport,
            new GeminiContentRequestConverter(new GeminiContentConverter(objectMapper)),
            new GeminiContentResponseConverter());
    final var configuration =
        new GeminiChatModelConfiguration(
            new GeminiConnection(
                new GeminiApiBackend(new GoogleGeminiApi(API_KEY, endpoint)),
                new GeminiModel(MODEL_ID, null),
                null));

    try (ChatModel chatModel = factory.create(configuration)) {
      chatModel.execute(new ChatRequest(executionContext(configuration), snapshot()));
    }
  }

  private static AgentExecutionContext executionContext(GeminiChatModelConfiguration model) {
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
    return new ConversationSnapshot(
        List.of(UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build()),
        List.of());
  }

  /**
   * Minimal hand-rolled HTTP forward proxy used to exercise {@link GeminiChatModelFactory}'s real
   * proxy-application branch end-to-end (rather than mocking {@link AgenticAiHttpProxySupport},
   * which leaves that branch untested). When credentials are configured, challenges the first
   * request with {@code 407 Proxy Authentication Required} so the vendor SDK's OkHttp {@code
   * proxyAuthenticator} actually has to respond, mirroring how a real authenticating proxy behaves.
   *
   * <p>Structurally identical to {@code AnthropicChatModelFactoryClientTest}'s helper (both SDKs
   * are OkHttp-based, so both forward a plain absolute-URI request for an {@code http://} target
   * rather than opening a {@code CONNECT} tunnel); only the returned response body differs, Gemini
   * SSE instead of Anthropic SSE.
   */
  private static final class FakeProxyServer implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final Thread acceptThread;
    private final @Nullable String username;
    private final @Nullable String password;
    private volatile @Nullable String lastRequestLine;
    private volatile @Nullable String lastProxyAuthorizationHeader;

    FakeProxyServer(@Nullable String username, @Nullable String password) throws IOException {
      this.username = username;
      this.password = password;
      this.serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
      this.acceptThread = new Thread(this::acceptLoop, "fake-proxy-accept");
      this.acceptThread.setDaemon(true);
      this.acceptThread.start();
    }

    @Nullable String lastRequestLine() {
      return lastRequestLine;
    }

    @Nullable String lastProxyAuthorizationHeader() {
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
