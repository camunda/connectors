/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration.FetchAgentCardOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientOperationConfiguration.SendMessageOperationConfiguration.Parameters;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientRequest;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientRequest.A2AClientRequestData;
import io.camunda.connector.agenticai.a2a.client.model.A2AClientRequest.A2AClientRequestData.ConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientAgentCardResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2AClientSendMessageResult.TaskState;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class A2AClientHandlerTest {

  @Mock private AgentCardFetcher agentCardFetcher;
  @Mock private MessageSender messageSender;
  @InjectMocks private A2AClientHandlerImpl handler;

  private static final ConnectionConfiguration CONNECTION =
      new ConnectionConfiguration("https://a2a.example.com", null);

  @Test
  void handleFetchAgentCard() {
    var operation = new FetchAgentCardOperationConfiguration();
    var request = new A2AClientRequest(new A2AClientRequestData(CONNECTION, operation));

    var expectedResult = new A2AClientAgentCardResult("name", "desc", List.of());
    when(agentCardFetcher.fetchAgentCard(CONNECTION)).thenReturn(expectedResult);

    var result = handler.handle(request);

    assertThat(result).isSameAs(expectedResult);
    verify(agentCardFetcher).fetchAgentCard(CONNECTION);
    verify(agentCardFetcher, never()).fetchAgentCardRaw(any());
    verify(messageSender, never()).sendMessage(any(), any());
  }

  @Test
  void handleSendMessage() {
    var agentCard = mock(AgentCard.class);
    var operation =
        new SendMessageOperationConfiguration(new Parameters("Hello", null), Duration.ofSeconds(1));
    var request = new A2AClientRequest(new A2AClientRequestData(CONNECTION, operation));

    when(agentCardFetcher.fetchAgentCardRaw(CONNECTION)).thenReturn(agentCard);
    var expectedSendResult =
        new A2AClientSendMessageResult(
            "resp-1", List.of(new TextContent("ok")), TaskState.COMPLETED);
    when(messageSender.sendMessage(operation, agentCard)).thenReturn(expectedSendResult);

    var result = handler.handle(request);

    assertThat(result).isSameAs(expectedSendResult);
    verify(agentCardFetcher).fetchAgentCardRaw(CONNECTION);
    verify(messageSender).sendMessage(operation, agentCard);
    verify(agentCardFetcher, never()).fetchAgentCard(CONNECTION);
  }
}
