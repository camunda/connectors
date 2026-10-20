/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.LinkedSystemPromptResolver.ResolvedLinkedSystemPrompt;
import io.camunda.connector.agenticai.aiagent.agent.AgentSubProcessRequestHandler;
import io.camunda.connector.agenticai.aiagent.model.AgentSubProcessExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.AgentSubProcessRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.AgentSubProcessV2Request;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentSubProcessV2FunctionTest {

  private static final String INLINE_PROMPT = "Inline instructions.";
  private static final String LINKED_PROMPT = "# Policy\n\nApprove up to $500.";
  private static final Map<String, String> HEADERS = Map.of("linkedResources", "[]");

  @Mock private AgentSubProcessRequestHandler agentRequestHandler;
  @Mock private LinkedSystemPromptResolver linkedSystemPromptResolver;
  @Mock private OutboundConnectorContext context;
  @Mock private JobContext jobContext;
  @Mock private AnthropicChatModelConfiguration provider;

  @Captor private ArgumentCaptor<AgentSubProcessExecutionContext> executionContextCaptor;

  private AgentSubProcessV2Function function;

  @BeforeEach
  void setUp() {
    function = new AgentSubProcessV2Function(agentRequestHandler, linkedSystemPromptResolver);
    when(context.getJobContext()).thenReturn(jobContext);
    when(jobContext.getCustomHeaders()).thenReturn(HEADERS);
  }

  @Test
  void usesLinkedPromptAloneWhenNoInlineSystemPromptIsConfigured() throws Exception {
    givenRequest(null);
    givenLinkedPrompt();

    function.execute(context);

    assertThat(capturedSystemPrompt()).isEqualTo(LINKED_PROMPT);
  }

  @Test
  void appendsLinkedPromptAfterInlinePrompt() throws Exception {
    givenRequest(new SystemPromptConfiguration(INLINE_PROMPT));
    givenLinkedPrompt();

    function.execute(context);

    assertThat(capturedSystemPrompt()).isEqualTo(INLINE_PROMPT + "\n\n" + LINKED_PROMPT);
  }

  @Test
  void keepsInlinePromptWithoutLinkedResource() throws Exception {
    givenRequest(new SystemPromptConfiguration(INLINE_PROMPT));

    function.execute(context);

    assertThat(capturedSystemPrompt()).isEqualTo(INLINE_PROMPT);
  }

  @Test
  void rejectsMissingInlineAndLinkedPromptBeforeModelCall() {
    givenRequest(null);

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .satisfies(
            e -> assertThat(((ConnectorException) e).getErrorCode()).isEqualTo("NO_SYSTEM_PROMPT"));
    verifyNoInteractions(agentRequestHandler);
  }

  @Test
  void rejectsBlankInlinePromptWithoutLinkedResource() {
    givenRequest(new SystemPromptConfiguration("  "));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("link a system prompt resource");
    verifyNoInteractions(agentRequestHandler);
  }

  private void givenRequest(SystemPromptConfiguration systemPrompt) {
    var data =
        new AgentSubProcessRequestData(
            systemPrompt, new UserPromptConfiguration("user prompt", null), null, null, null, null);
    when(context.bindVariables(AgentSubProcessV2Request.class))
        .thenReturn(new AgentSubProcessV2Request(List.of(), null, List.of(), provider, data));
    lenient()
        .when(agentRequestHandler.handleRequest(any()))
        .thenReturn(
            AgentSubProcessConnectorResponse.builder()
                .variables(Map.of())
                .elementActivations(List.of())
                .build());
  }

  private void givenLinkedPrompt() {
    when(linkedSystemPromptResolver.resolve(HEADERS))
        .thenReturn(
            new ResolvedLinkedSystemPrompt(
                LINKED_PROMPT, "dispute-compliance-policy.md", "latest", 3, 42L));
  }

  private String capturedSystemPrompt() {
    verify(agentRequestHandler).handleRequest(executionContextCaptor.capture());
    return executionContextCaptor.getValue().configuration().systemPrompt().prompt();
  }
}
