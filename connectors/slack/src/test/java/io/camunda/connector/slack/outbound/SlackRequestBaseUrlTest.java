/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.Message;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.api.Test;

@WireMockTest
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
  void defaultBaseUrl_usesTheInjectedSlackClient() throws Exception {
    var slack = mock(Slack.class);
    var methods = mock(MethodsClient.class);
    var chatResponse = mock(ChatPostMessageResponse.class);
    when(slack.methods(TOKEN)).thenReturn(methods);
    when(methods.chatPostMessage(any(ChatPostMessageRequest.class))).thenReturn(chatResponse);
    when(chatResponse.isOk()).thenReturn(true);
    when(chatResponse.getMessage()).thenReturn(new Message());

    bind("\"baseUrl\": \"https://slack.com/api\",").invoke(slack);

    verify(slack).methods(TOKEN);
  }

  @Test
  void defaultBaseUrl_matchesTheSlackSdkDefaultEndpoint() {
    assertThat(SlackRequest.DEFAULT_BASE_URL)
        .isEqualTo(Slack.getInstance().getConfig().getMethodsEndpointUrlPrefix());
  }

  @Test
  void blankBaseUrl_isTreatedAsUnset() {
    assertThat(bind("\"baseUrl\": \"  \",").baseUrl()).isNull();
  }

  @Test
  void baseUrl_redirectsRequestsToTheConfiguredHost(WireMockRuntimeInfo wm) throws Exception {
    stubFor(
        post("/api/chat.postMessage")
            .willReturn(
                okJson(
                    "{\"ok\":true,\"channel\":\"C123ABC456\",\"ts\":\"1.1\",\"message\":{\"type\":\"message\"}}")));

    var response =
        bind("\"baseUrl\": \"" + wm.getHttpBaseUrl() + "/api\",").invoke(mock(Slack.class));

    assertThat(response).isNotNull();
    WireMock.verify(
        1,
        postRequestedFor(urlEqualTo("/api/chat.postMessage"))
            .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
  }
}
