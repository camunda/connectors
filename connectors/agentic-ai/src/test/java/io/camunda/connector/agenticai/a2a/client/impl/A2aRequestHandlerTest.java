/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.api.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.api.A2aMessageSender;
import io.camunda.connector.agenticai.a2a.client.model.A2aOperationConfiguration.FetchAgentCardOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2aOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.A2aOperationConfiguration.SendMessageOperationConfiguration.Parameters;
import io.camunda.connector.agenticai.a2a.client.model.A2aRequest;
import io.camunda.connector.agenticai.a2a.client.model.A2aRequest.A2aClientRequestData;
import io.camunda.connector.agenticai.a2a.client.model.A2aRequest.A2aClientRequestData.ConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aAgentCardResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aArtifact;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aTaskStatus;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class A2aRequestHandlerTest {

  @Mock private A2aAgentCardFetcher agentCardFetcher;
  @Mock private A2aMessageSender messageSender;
  @InjectMocks private A2aRequestHandlerImpl handler;

  private static final ConnectionConfiguration CONNECTION =
      new ConnectionConfiguration("https://a2a.example.com", null);

  @Test
  void handleFetchAgentCard() {
    var operation = new FetchAgentCardOperationConfiguration();
    var request = new A2aRequest(new A2aClientRequestData(CONNECTION, operation));

    var expectedResult = new A2aAgentCardResult("name", "desc", List.of());
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
    var request = new A2aRequest(new A2aClientRequestData(CONNECTION, operation));

    when(agentCardFetcher.fetchAgentCardRaw(CONNECTION)).thenReturn(agentCard);
    var expectedSendResult =
        new A2aSendMessageResult.A2aTaskResult(
            A2aTask.builder()
                .id("task-1")
                .contextId("context-1")
                .status(A2aTaskStatus.builder().state(A2aTaskStatus.TaskState.COMPLETED).build())
                .artifacts(
                    List.of(
                        A2aArtifact.builder()
                            .artifactId("artifact-1")
                            .name("artifact1")
                            .contents(List.of(new TextContent("ok", null)))
                            .build()))
                .build());
    when(messageSender.sendMessage(agentCard, operation)).thenReturn(expectedSendResult);

    var result = handler.handle(request);

    assertThat(result).isSameAs(expectedSendResult);
    verify(agentCardFetcher).fetchAgentCardRaw(CONNECTION);
    verify(messageSender).sendMessage(agentCard, operation);
    verify(agentCardFetcher, never()).fetchAgentCard(CONNECTION);
  }
}
