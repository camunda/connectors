/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.Message;
import com.sun.net.httpserver.HttpServer;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SlackRequestBaseUrlTest {

  private static final String TOKEN = "xoxb-token";

  private static SlackRequest<?> bind(String baseUrlJson) {
    return OutboundConnectorContextBuilder.create()
        .validation(new DefaultValidationProvider())
        .variables(
            """
            {
              "token": "%s",
              %s
              "method": "chat.postMessage",
              "data": { "channel": "C123ABC456", "text": "hello" }
            }
            """
                .formatted(TOKEN, baseUrlJson))
        .build()
        .bindVariables(SlackRequest.class);
  }

  @Test
  void noBaseUrl_usesTheInjectedSlackClient() throws Exception {
    var slack = mock(Slack.class);
    var methods = mock(MethodsClient.class);
    var chatResponse = mock(ChatPostMessageResponse.class);
    when(slack.methods(TOKEN)).thenReturn(methods);
    when(methods.chatPostMessage(any(ChatPostMessageRequest.class))).thenReturn(chatResponse);
    when(chatResponse.isOk()).thenReturn(true);
    when(chatResponse.getMessage()).thenReturn(new Message());

    bind("").invoke(slack);

    verify(slack).methods(TOKEN);
    verifyNoMoreInteractions(slack);
  }

  @Test
  void blankBaseUrl_isTreatedAsUnset() {
    assertThat(bind("\"baseUrl\": \"  \",").baseUrl()).isNull();
  }

  @Test
  void baseUrl_redirectsRequestsToTheConfiguredHost() throws Exception {
    var path = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          path.set(exchange.getRequestURI().getPath());
          var body =
              "{\"ok\":true,\"channel\":\"C123ABC456\",\"ts\":\"1.1\",\"message\":{\"type\":\"message\"}}";
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          var bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      var baseUrl = "http://localhost:" + server.getAddress().getPort() + "/api";

      var response = bind("\"baseUrl\": \"" + baseUrl + "\",").invoke(mock(Slack.class));

      assertThat(path.get()).isEqualTo("/api/chat.postMessage");
      assertThat(response).isNotNull();
    } finally {
      server.stop(0);
    }
  }
}
