/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.fetch.ResourceContentGetRequest;
import io.camunda.client.api.fetch.ResourceGetRequest;
import io.camunda.client.api.response.Resource;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.aiagent.agent.AgentTaskRequestHandler;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentTaskExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.AgentTaskRequestData;
import io.camunda.connector.agenticai.aiagent.model.request.AgentTaskV2Request;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentTaskV2FunctionTest {

  private static final long RESOURCE_KEY = 2251799813685249L;
  private static final String INLINE_PROMPT =
      "Resolve the linked governed instructions before answering.";
  private static final String LINKED_PROMPT =
      """
      # Claims review instructions

      - Verify every claim against the supplied evidence.
      - Escalate uncertain decisions to a human reviewer.""";

  @Mock private ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  @Mock private AgentTaskRequestHandler agentRequestHandler;
  @Mock private CamundaClient camundaClient;
  @Mock private ResourceContentGetRequest resourceContentGetRequest;
  @Mock private ResourceGetRequest resourceGetRequest;
  @Mock private Resource resource;
  @Mock private OutboundConnectorContext context;
  @Mock private JobContext jobContext;
  @Mock private AnthropicChatModelConfiguration provider;
  @Mock private AgentContext agentContext;

  @Captor private ArgumentCaptor<AgentTaskExecutionContext> executionContextCaptor;

  private AgentTaskV2Function function;

  @BeforeEach
  void setUp() {
    function =
        new AgentTaskV2Function(
            toolElementsResolver,
            agentRequestHandler,
            camundaClient,
            ConnectorsObjectMapperSupplier.getCopy());
    when(context.getJobContext()).thenReturn(jobContext);
  }

  @Test
  void preservesInlineSystemPromptWithoutLinkedResource() {
    givenRequest(INLINE_PROMPT);
    when(jobContext.getCustomHeaders()).thenReturn(Map.of());

    function.execute(context);

    assertThat(capturedSystemPrompt()).isEqualTo(INLINE_PROMPT);
    verifyNoInteractions(camundaClient);
  }

  @Test
  void usesLinkedSystemPromptVerbatimWithoutInlinePromptConfiguration() {
    givenRequest(null);
    givenLinkedSystemPrompt();

    function.execute(context);

    assertThat(capturedSystemPrompt()).isEqualTo(LINKED_PROMPT);
  }

  @Test
  void appendsLinkedSystemPromptVerbatimAfterInlinePrompt() {
    givenRequest(INLINE_PROMPT);
    givenLinkedSystemPrompt();

    function.execute(context);

    assertThat(capturedSystemPrompt()).isEqualTo(INLINE_PROMPT + "\n\n" + LINKED_PROMPT);
  }

  @Test
  void includesResolvedLinkedSystemPromptProvenanceWithoutChangingExistingResultFields() {
    givenRequest(INLINE_PROMPT);
    givenLinkedSystemPrompt();
    var responseJson = Map.of("decision", "manual-review");
    when(agentRequestHandler.handleRequest(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              var executionContext = invocation.getArgument(0, AgentTaskExecutionContext.class);
              executionContext.recordComposedSystemPrompt(
                  INLINE_PROMPT + "\n\n" + LINKED_PROMPT + "\n\nCamunda contribution");
              return new AgentTaskConnectorResponse(
                  AgentResponse.builder().context(agentContext).responseJson(responseJson).build(),
                  null);
            });

    var result = function.execute(context).agentResponse();

    assertThat(result).isNotNull();
    assertThat(result.context()).isSameAs(agentContext);
    assertThat(result.responseJson()).isSameAs(responseJson);
    assertThat(result.systemPrompt())
        .isEqualTo(
            new io.camunda.connector.agenticai.aiagent.model.SystemPromptProvenance(
                "linked",
                "claims-review-instructions.md",
                "latest",
                2,
                INLINE_PROMPT + "\n\n" + LINKED_PROMPT + "\n\nCamunda contribution"));
  }

  @Test
  void ignoresLinkedResourcesWithOtherTypesOrLinkNames() {
    givenRequest(INLINE_PROMPT);
    when(jobContext.getCustomHeaders())
        .thenReturn(
            Map.of(
                "linkedResources",
                """
                [
                  {"resourceKey":"1","resourceType":"form","linkName":"systemPrompt"},
                  {"resourceKey":"2","resourceType":"system-prompt","linkName":"otherPrompt"}
                ]"""));

    function.execute(context);

    assertThat(capturedSystemPrompt()).isEqualTo(INLINE_PROMPT);
    verifyNoInteractions(camundaClient);
  }

  @Test
  void rejectsMalformedLinkedResourceMetadata() {
    givenRequest(INLINE_PROMPT);
    when(jobContext.getCustomHeaders()).thenReturn(Map.of("linkedResources", "not-json"));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Failed to parse linked resource metadata");
    verifyNoInteractions(agentRequestHandler, camundaClient);
  }

  @Test
  void rejectsNullLinkedResourceMetadata() {
    givenRequest(INLINE_PROMPT);
    when(jobContext.getCustomHeaders()).thenReturn(Map.of("linkedResources", "null"));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Failed to parse linked resource metadata");
    verifyNoInteractions(agentRequestHandler, camundaClient);
  }

  @Test
  void rejectsMultipleLinkedSystemPromptsBeforeModelCall() {
    givenRequest(INLINE_PROMPT);
    when(jobContext.getCustomHeaders())
        .thenReturn(
            Map.of(
                "linkedResources",
                """
                [
                  {"resourceKey":"1","resourceType":"system-prompt","linkName":"systemPrompt"},
                  {"resourceKey":"2","resourceType":"system-prompt","linkName":"systemPrompt"}
                ]
                """));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("multiple linked system prompt resources");
    verifyNoInteractions(agentRequestHandler, camundaClient);
  }

  @Test
  void rejectsLinkedSystemPromptWithoutResolvedResourceKey() {
    givenRequest(INLINE_PROMPT);
    when(jobContext.getCustomHeaders())
        .thenReturn(
            Map.of(
                "linkedResources",
                """
                [{"resourceType":"system-prompt","linkName":"systemPrompt"}]
                """));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("has no resolved resource key");
    verifyNoInteractions(agentRequestHandler, camundaClient);
  }

  @Test
  void rejectsLinkedSystemPromptWithoutBindingMetadata() {
    givenRequest(INLINE_PROMPT);
    when(jobContext.getCustomHeaders())
        .thenReturn(
            Map.of(
                "linkedResources",
                """
                [{"resourceKey":"%d","resourceType":"system-prompt","linkName":"systemPrompt"}]
                """
                    .formatted(RESOURCE_KEY)));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("has no binding metadata");
    verifyNoInteractions(agentRequestHandler, camundaClient);
  }

  @Test
  void rejectsLinkedSystemPromptRetrievalFailureBeforeModelCall() {
    givenRequest(INLINE_PROMPT);
    when(jobContext.getCustomHeaders())
        .thenReturn(
            Map.of(
                "linkedResources",
                """
                [{"resourceKey":"%d","resourceType":"system-prompt","linkName":"systemPrompt"}]
                """
                    .formatted(RESOURCE_KEY),
                "systemPromptBinding",
                "latest"));
    when(camundaClient.newResourceGetRequest(RESOURCE_KEY)).thenReturn(resourceGetRequest);
    when(resourceGetRequest.execute()).thenReturn(resource);
    when(camundaClient.newResourceContentBinaryGetRequest(RESOURCE_KEY))
        .thenReturn(resourceContentGetRequest);
    when(resourceContentGetRequest.execute()).thenThrow(new IllegalStateException("unavailable"));

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining(
            "Failed to retrieve linked system prompt resource with key %d".formatted(RESOURCE_KEY));
    verifyNoInteractions(agentRequestHandler);
  }

  private void givenRequest(String inlinePrompt) {
    var data =
        new AgentTaskRequestData(
            null,
            inlinePrompt == null ? null : new SystemPromptConfiguration(inlinePrompt),
            new UserPromptConfiguration("user prompt", null),
            null,
            null,
            null,
            null);
    when(context.bindVariables(AgentTaskV2Request.class))
        .thenReturn(new AgentTaskV2Request(provider, data));
    org.mockito.Mockito.lenient()
        .when(agentRequestHandler.handleRequest(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new AgentTaskConnectorResponse(null, null));
  }

  private void givenLinkedSystemPrompt() {
    when(jobContext.getCustomHeaders())
        .thenReturn(
            Map.of(
                "linkedResources",
                """
                [{"resourceKey":"%d","resourceType":"system-prompt","linkName":"systemPrompt"}]
                """
                    .formatted(RESOURCE_KEY),
                "systemPromptBinding",
                "latest"));
    when(camundaClient.newResourceGetRequest(RESOURCE_KEY)).thenReturn(resourceGetRequest);
    when(resourceGetRequest.execute()).thenReturn(resource);
    when(resource.getResourceId()).thenReturn("claims-review-instructions.md");
    when(resource.getVersion()).thenReturn(2);
    when(camundaClient.newResourceContentBinaryGetRequest(RESOURCE_KEY))
        .thenReturn(resourceContentGetRequest);
    when(resourceContentGetRequest.execute()).thenReturn(LINKED_PROMPT);
  }

  private String capturedSystemPrompt() {
    verify(agentRequestHandler).handleRequest(executionContextCaptor.capture());
    return executionContextCaptor.getValue().configuration().systemPrompt().prompt();
  }
}
