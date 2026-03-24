/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.jobworker;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS_PROCESS_VARIABLES;
import static io.camunda.connector.agenticai.util.WireMockUtils.assertJobCompletionRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.protocol.rest.JobResult;
import io.camunda.client.protocol.rest.JobResultActivateElement;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@WireMockTest
@ExtendWith(MockitoExtension.class)
class AiAgentJobCompletionTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ActivatedJob job;

  private CamundaClient camundaClient;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntimeInfo) throws URISyntaxException {
    camundaClient =
        spy(
            CamundaClient.newClientBuilder()
                .preferRestOverGrpc(true)
                .restAddress(new URI(wireMockRuntimeInfo.getHttpBaseUrl()))
                .build());

    lenient().when(job.getKey()).thenReturn(123456L);

    stubFor(
        post(urlPathEqualTo("/v2/jobs/123456/completion")).willReturn(aResponse().withStatus(204)));
  }

  @Test
  void completesJobWithoutToolCalls() {
    var completion =
        AiAgentJobCompletion.builder()
            .agentResponse(null)
            .completionConditionFulfilled(false)
            .cancelRemainingInstances(false)
            .variables(Map.of())
            .build();

    completion.prepareCompleteCommand(camundaClient, job, Map.of()).send().join();

    assertJobCompletionRequest(
        request -> {
          assertThat(request.getVariables()).isEmpty();
          assertThat(request.getResult())
              .isNotNull()
              .isInstanceOfSatisfying(
                  JobResult.class,
                  adHoc -> {
                    assertThat(adHoc.getIsCompletionConditionFulfilled()).isFalse();
                    assertThat(adHoc.getIsCancelRemainingInstances()).isFalse();
                    assertThat(adHoc.getActivateElements()).isEmpty();
                  });
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void completesWithCompletionConditionFulfilled(boolean cancelRemainingInstances) {
    var completion =
        AiAgentJobCompletion.builder()
            .agentResponse(null)
            .completionConditionFulfilled(true)
            .cancelRemainingInstances(cancelRemainingInstances)
            .variables(Map.of("agent", Map.of("responseText", "Done")))
            .build();

    completion.prepareCompleteCommand(camundaClient, job, Map.of()).send().join();

    assertJobCompletionRequest(
        request -> {
          assertThat(request.getVariables()).containsEntry("agent", Map.of("responseText", "Done"));
          assertThat(request.getResult())
              .isNotNull()
              .isInstanceOfSatisfying(
                  JobResult.class,
                  adHoc -> {
                    assertThat(adHoc.getIsCompletionConditionFulfilled()).isTrue();
                    assertThat(adHoc.getIsCancelRemainingInstances())
                        .isEqualTo(cancelRemainingInstances);
                    assertThat(adHoc.getActivateElements()).isEmpty();
                  });
        });
  }

  @Test
  void completesWithoutElementActivationWhenAgentResponseHasEmptyToolCalls() {
    var agentResponse =
        AgentResponse.builder().context(AgentContext.empty()).toolCalls(List.of()).build();

    var completion =
        AiAgentJobCompletion.builder()
            .agentResponse(agentResponse)
            .completionConditionFulfilled(true)
            .cancelRemainingInstances(false)
            .variables(Map.of("agent", Map.of("responseText", "Done")))
            .build();

    completion.prepareCompleteCommand(camundaClient, job, Map.of()).send().join();

    assertJobCompletionRequest(
        request -> {
          assertThat(request.getVariables()).containsEntry("agent", Map.of("responseText", "Done"));
          assertThat(request.getResult())
              .isNotNull()
              .isInstanceOfSatisfying(
                  JobResult.class,
                  adHoc -> {
                    assertThat(adHoc.getIsCompletionConditionFulfilled()).isTrue();
                    assertThat(adHoc.getActivateElements()).isEmpty();
                  });
        });
  }

  @Test
  void usesRecordVariablesNotMethodParameter() {
    var recordVariables = Map.<String, Object>of("fromRecord", true);
    var methodVariables = Map.<String, Object>of("fromMethod", true);

    var completion =
        AiAgentJobCompletion.builder()
            .completionConditionFulfilled(true)
            .cancelRemainingInstances(false)
            .variables(recordVariables)
            .build();

    completion.prepareCompleteCommand(camundaClient, job, methodVariables).send().join();

    assertJobCompletionRequest(
        request -> {
          assertThat(request.getVariables()).containsEntry("fromRecord", true);
          assertThat(request.getVariables()).doesNotContainKey("fromMethod");
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void completesWithActivatingElements(boolean cancelRemainingInstances) {
    var toolCalls = TOOL_CALLS_PROCESS_VARIABLES;
    var agentResponse =
        AgentResponse.builder().context(AgentContext.empty()).toolCalls(toolCalls).build();

    var completion =
        AiAgentJobCompletion.builder()
            .agentResponse(agentResponse)
            .completionConditionFulfilled(false)
            .cancelRemainingInstances(cancelRemainingInstances)
            .variables(Map.of("agentContext", AgentContext.empty(), "toolCallResults", List.of()))
            .build();

    completion.prepareCompleteCommand(camundaClient, job, Map.of()).send().join();

    assertJobCompletionRequest(
        request -> {
          assertThat(request.getVariables()).containsOnlyKeys("agentContext", "toolCallResults");
          assertThat(request.getResult())
              .isNotNull()
              .isInstanceOfSatisfying(
                  JobResult.class,
                  adHoc -> {
                    assertThat(adHoc.getIsCompletionConditionFulfilled()).isFalse();
                    assertThat(adHoc.getIsCancelRemainingInstances())
                        .isEqualTo(cancelRemainingInstances);
                    assertThat(adHoc.getActivateElements())
                        .extracting(
                            JobResultActivateElement::getElementId,
                            JobResultActivateElement::getVariables)
                        .containsExactly(
                            tuple(
                                "getWeather",
                                Map.ofEntries(
                                    Map.entry("toolCall", asMap(toolCalls.getFirst())),
                                    Map.entry("toolCallResult", ""))),
                            tuple(
                                "getDateTime",
                                Map.ofEntries(
                                    Map.entry("toolCall", asMap(toolCalls.get(1))),
                                    Map.entry("toolCallResult", ""))));
                  });
        });
  }

  @Test
  void rejectIgnoreErrorIsTrue() {
    assertThat(AiAgentJobCompletion.builder().build().rejectIgnoreError()).isTrue();
  }

  private Map<String, Object> asMap(final ToolCallProcessVariable toolCallProcessVariable) {
    return OBJECT_MAPPER.convertValue(toolCallProcessVariable, new TypeReference<>() {});
  }
}
