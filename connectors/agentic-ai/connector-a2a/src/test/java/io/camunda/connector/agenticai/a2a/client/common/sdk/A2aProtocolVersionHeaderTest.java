/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import io.camunda.connector.agenticai.a2a.client.common.configuration.A2aClientCommonConfigurationProperties.TransportConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.configuration.A2aClientCommonConfigurationProperties.TransportConfiguration.GrpcConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.model.A2aConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.sdk.convert.A2aDocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.common.sdk.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.common.sdk.http.A2aHttpClientFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.a2aproject.sdk.client.http.JdkA2AHttpClient;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies that the A2A version header is sent on the Agent Card request and on every call. */
class A2aProtocolVersionHeaderTest {

  private final Map<String, String> versionHeaderByRequest = new ConcurrentHashMap<>();
  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    baseUrl = "http://localhost:" + server.getAddress().getPort();
    final var cardJson = JsonUtil.toJson(card());
    server.createContext(
        "/",
        exchange -> {
          final var version = exchange.getRequestHeaders().getFirst("A2A-Version");
          versionHeaderByRequest.put(
              exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath(),
              String.valueOf(version));
          final byte[] body =
              exchange.getRequestURI().getPath().endsWith("agent-card.json")
                  ? cardJson.getBytes(StandardCharsets.UTF_8)
                  : "{}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void sendsVersionHeaderOnAgentCardRequest() {
    final var fetcher = new A2aAgentCardFetcherImpl(httpClientFactory());

    fetcher.fetchAgentCardRaw(new A2aConnectionConfiguration(baseUrl, null));

    assertThat(versionHeaderByRequest).containsEntry("GET /.well-known/agent-card.json", "1.0");
  }

  @Test
  void sendsVersionHeaderOnJsonRpcAndRestCalls() {
    for (String binding : List.of("JSONRPC", "HTTP+JSON")) {
      versionHeaderByRequest.clear();
      final var card = new A2aSdkAgentCard(card(binding));
      try (var client =
          new A2aSdkClientFactoryImpl(
                  new TransportConfiguration(new GrpcConfiguration(false)),
                  httpClientFactory(),
                  mock(A2aSendMessageResponseHandler.class),
                  mock(A2aDocumentToPartConverter.class),
                  mock(A2aSdkObjectConverter.class))
              .buildClient(
                  card, A2aSdkClientListener.NOOP, new A2aSdkClientConfig(0, true, null))) {
        try {
          client.sendMessage(new A2aOutgoingMessage("hello", null, null, null, null));
        } catch (RuntimeException ignored) {
          // the stub server returns an empty body, only the request headers matter
        }
      }

      assertThat(versionHeaderByRequest)
          .isNotEmpty()
          .allSatisfy((k, v) -> assertThat(v).isEqualTo("1.0"));
    }
  }

  private A2aHttpClientFactory httpClientFactory() {
    final var factory = mock(A2aHttpClientFactory.class);
    when(factory.createHttpClient()).thenReturn(new JdkA2AHttpClient());
    return factory;
  }

  private AgentCard card() {
    return card("JSONRPC");
  }

  private AgentCard card(String binding) {
    return AgentCard.builder()
        .name("Test agent")
        .description("Test agent")
        .version("1.0.0")
        .capabilities(AgentCapabilities.builder().build())
        .defaultInputModes(List.of("text"))
        .defaultOutputModes(List.of("text"))
        .skills(List.of())
        .supportedInterfaces(List.of(new AgentInterface(binding, baseUrl)))
        .build();
  }
}
