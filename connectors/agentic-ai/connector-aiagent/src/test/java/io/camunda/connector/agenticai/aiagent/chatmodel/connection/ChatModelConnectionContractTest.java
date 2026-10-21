/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.connection;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static io.camunda.connector.agenticai.aiagent.AgentConfigurationFixture.agentConfiguration;
import static io.camunda.connector.agenticai.aiagent.testsupport.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.aws.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.FoundryCredentialResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.configuration.AgenticAiNativeProvidersConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral.MistralConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.proxy.aws.AwsSdkHttpProxySupport;
import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.authentication.OAuthClientCredentialsTokenResolver;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The connection settings behave the same for every native provider: the timeout of a model call is
 * enforced, the call goes through the configured proxy, and it bypasses the proxy for a non-proxy
 * host. Each provider is driven through its own factory against a stub endpoint, so the test holds
 * whatever the SDK of the provider does with the resolved settings.
 */
@WireMockTest
class ChatModelConnectionContractTest {

  /**
   * The SDKs retry a call which timed out, so the call takes a multiple of the timeout. A timeout
   * which is not enforced makes it last as long as the 60 seconds which the endpoint stalls.
   */
  private static final Duration TIMEOUT = Duration.ofMillis(700);

  private static final Duration GENEROUS_TIMEOUT = Duration.ofSeconds(5);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final String previousNonProxyHosts = System.getProperty("http.nonProxyHosts");

  @AfterEach
  void restoreNonProxyHosts() {
    if (previousNonProxyHosts == null) {
      System.clearProperty("http.nonProxyHosts");
    } else {
      System.setProperty("http.nonProxyHosts", previousNonProxyHosts);
    }
  }

  @ParameterizedTest
  @EnumSource(Provider.class)
  void enforcesTheTimeoutOfTheModelCall(Provider provider, WireMockRuntimeInfo wireMock) {
    stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200).withFixedDelay(60_000)));

    try (var model = provider.create(ProxyConfiguration.NONE, wireMock.getHttpBaseUrl(), TIMEOUT)) {
      final long start = System.nanoTime();
      assertThatThrownBy(() -> model.execute(request())).isInstanceOf(RuntimeException.class);
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(45));
    }
  }

  @ParameterizedTest
  @EnumSource(Provider.class)
  void connectsThroughTheConfiguredProxy(Provider provider, WireMockRuntimeInfo wireMock)
      throws IOException {
    try (var proxy = new RecordingProxy()) {
      final var endpoint = "http://llm.example:" + wireMock.getHttpPort();

      try (var model = provider.create(proxy.configuration(), endpoint, GENEROUS_TIMEOUT)) {
        assertThatThrownBy(() -> model.execute(request())).isInstanceOf(RuntimeException.class);
      }

      assertThat(proxy.lastRequestLine()).contains("llm.example");
    }
  }

  @ParameterizedTest
  @EnumSource(Provider.class)
  void bypassesTheProxyForANonProxyHost(Provider provider, WireMockRuntimeInfo wireMock)
      throws IOException {
    System.setProperty("http.nonProxyHosts", "localhost");
    stubFor(any(anyUrl()).willReturn(aResponse().withStatus(400)));

    try (var proxy = new RecordingProxy()) {
      try (var model =
          provider.create(proxy.configuration(), wireMock.getHttpBaseUrl(), GENEROUS_TIMEOUT)) {
        assertThatThrownBy(() -> model.execute(request())).isInstanceOf(RuntimeException.class);
      }

      assertThat(proxy.lastRequestLine()).isNull();
      verify(anyRequestedFor(anyUrl()));
    }
  }

  private ChatRequest request() {
    final var executionContext = mock(AgentExecutionContext.class);
    when(executionContext.configuration()).thenReturn(agentConfiguration().build());
    return new ChatRequest(
        executionContext, new ConversationSnapshot(List.of(userMessage("Hello")), List.of()));
  }

  /** The native providers, each creating its chat model through its own factory. */
  enum Provider {
    OPENAI {
      @Override
      String errorBody(String message) {
        return "{\"error\":{\"message\":\"%s\",\"type\":\"invalid_request_error\",\"code\":\"bad_request\"}}"
            .formatted(message);
      }

      @Override
      String errorType() {
        return "bad_request";
      }

      @Override
      String contextWindowErrorBody() {
        return "{\"error\":{\"message\":\"too long\",\"type\":\"invalid_request_error\",\"code\":\"context_length_exceeded\"}}";
      }

      @Override
      ChatModel create(
          ProxyConfiguration proxy, String endpoint, Duration timeout, ObjectMapper om) {
        return factories(proxy)
            .aiAgentOpenAiChatModelFactory(
                resolver(proxy), mock(FoundryCredentialResolver.class), om)
            .create(
                new OpenAiConfiguration(
                    new OpenAiConfiguration.Api.Completions(null, null, null, null),
                    new OpenAiConfiguration.Backend.Custom(
                        endpoint, new EndpointAuthentication.None(), RequestCustomizations.NONE),
                    "model",
                    timeout));
      }
    },
    ANTHROPIC {
      @Override
      String errorBody(String message) {
        return "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"%s\"}}"
            .formatted(message);
      }

      @Override
      String errorType() {
        return "invalid_request_error";
      }

      @Override
      ChatModel create(
          ProxyConfiguration proxy, String endpoint, Duration timeout, ObjectMapper om) {
        return factories(proxy)
            .aiAgentAnthropicChatModelFactory(
                resolver(proxy), mock(FoundryCredentialResolver.class), om)
            .create(
                new AnthropicConfiguration(
                    new AnthropicConfiguration.Backend.Custom(
                        endpoint, new EndpointAuthentication.None(), RequestCustomizations.NONE),
                    "model",
                    timeout,
                    null,
                    null,
                    false,
                    1024,
                    null,
                    null,
                    null));
      }
    },
    MISTRAL {
      @Override
      String errorBody(String message) {
        return "{\"error\":{\"message\":\"%s\",\"type\":\"invalid_request_error\",\"code\":\"bad_request\"}}"
            .formatted(message);
      }

      @Override
      String errorType() {
        return "bad_request";
      }

      @Override
      String contextWindowErrorBody() {
        return "{\"error\":{\"message\":\"too long\",\"type\":\"invalid_request_error\",\"code\":\"context_length_exceeded\"}}";
      }

      @Override
      ChatModel create(
          ProxyConfiguration proxy, String endpoint, Duration timeout, ObjectMapper om) {
        return factories(proxy)
            .aiAgentMistralChatModelFactory(resolver(proxy), om)
            .create(
                new MistralConfiguration(
                    new MistralConfiguration.Backend.Custom(
                        endpoint, new EndpointAuthentication.None(), RequestCustomizations.NONE),
                    "model",
                    timeout,
                    null,
                    null,
                    null,
                    null));
      }
    },
    GEMINI {
      @Override
      String errorBody(String message) {
        return "{\"error\":{\"code\":400,\"message\":\"%s\",\"status\":\"INVALID_ARGUMENT\"}}"
            .formatted(message);
      }

      @Override
      String errorType() {
        return "INVALID_ARGUMENT";
      }

      @Override
      String contextWindowErrorBody() {
        return errorBody(
            "The input token count (9) exceeds the maximum number of tokens allowed (1).");
      }

      @Override
      ChatModel create(
          ProxyConfiguration proxy, String endpoint, Duration timeout, ObjectMapper om) {
        return factories(proxy)
            .aiAgentGeminiChatModelFactory(resolver(proxy), om)
            .create(
                new GeminiConfiguration(
                    new GeminiConfiguration.Backend.Api("api-key", endpoint),
                    "model",
                    timeout,
                    null,
                    null,
                    null,
                    null,
                    null));
      }
    },
    BEDROCK {
      @Override
      HttpHeaders errorHeaders() {
        return new HttpHeaders(
            HttpHeader.httpHeader("Content-Type", "application/json"),
            HttpHeader.httpHeader("x-amzn-ErrorType", "ValidationException"));
      }

      @Override
      String errorBody(String message) {
        return "{\"message\":\"%s\"}".formatted(message);
      }

      @Override
      String errorType() {
        return "ValidationException";
      }

      @Override
      ChatModel create(
          ProxyConfiguration proxy, String endpoint, Duration timeout, ObjectMapper om) {
        return factories(proxy)
            .aiAgentBedrockConverseChatModelFactory(
                resolver(proxy), new AwsSdkHttpProxySupport(proxy), om)
            .create(
                new BedrockConfiguration(
                    "us-east-1",
                    endpoint,
                    new AwsAuthentication.StaticCredentials("AKIA", "secret"),
                    RequestCustomizations.NONE,
                    timeout,
                    "model",
                    false,
                    null,
                    null,
                    null));
      }
    };

    abstract ChatModel create(
        ProxyConfiguration proxy, String endpoint, Duration timeout, ObjectMapper objectMapper);

    /** The headers of an error response of the API of the provider. */
    HttpHeaders errorHeaders() {
      return new HttpHeaders(HttpHeader.httpHeader("Content-Type", "application/json"));
    }

    /** The body of an error response of the API of the provider. */
    abstract String errorBody(String message);

    /** What the connector reports as the type of the error of {@link #errorBody}. */
    abstract String errorType();

    /** The body of the error response to a request which exceeds the context window. */
    String contextWindowErrorBody() {
      throw new UnsupportedOperationException();
    }

    ChatModel create(ProxyConfiguration proxy, String endpoint, Duration timeout) {
      return create(proxy, endpoint, timeout, new ObjectMapper());
    }

    static AgenticAiNativeProvidersConfiguration factories(ProxyConfiguration proxy) {
      return new AgenticAiNativeProvidersConfiguration();
    }

    static ConnectionResolver resolver(ProxyConfiguration proxy) {
      return new ConnectionResolver(
          new AgenticAiHttpProxySupport(proxy), mock(OAuthClientCredentialsTokenResolver.class));
    }
  }

  /**
   * A forward proxy which records the request line of the first request and rejects it with a
   * status which the SDKs do not retry.
   */
  private static final class RecordingProxy implements AutoCloseable {
    private final ServerSocket serverSocket;
    private volatile String lastRequestLine;

    RecordingProxy() throws IOException {
      serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
      final var thread = new Thread(this::acceptLoop, "recording-proxy");
      thread.setDaemon(true);
      thread.start();
    }

    ProxyConfiguration configuration() {
      return scheme ->
          Optional.of(
              new ProxyConfiguration.ProxyDetails(
                  ProxyConfiguration.SCHEME_HTTP,
                  "127.0.0.1",
                  serverSocket.getLocalPort(),
                  null,
                  null));
    }

    String lastRequestLine() {
      return lastRequestLine;
    }

    private void acceptLoop() {
      while (!serverSocket.isClosed()) {
        try (Socket socket = serverSocket.accept()) {
          final var requestLine = readLine(socket.getInputStream());
          if (requestLine != null) {
            lastRequestLine = requestLine;
          }
          socket
              .getOutputStream()
              .write(
                  "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                      .getBytes(StandardCharsets.US_ASCII));
        } catch (IOException e) {
          return;
        }
      }
    }

    private static String readLine(InputStream in) throws IOException {
      final var line = new StringBuilder();
      int c;
      while ((c = in.read()) != -1 && c != '\n') {
        if (c != '\r') {
          line.append((char) c);
        }
      }
      return c == -1 && line.isEmpty() ? null : line.toString();
    }

    @Override
    public void close() throws IOException {
      serverSocket.close();
    }
  }
}
