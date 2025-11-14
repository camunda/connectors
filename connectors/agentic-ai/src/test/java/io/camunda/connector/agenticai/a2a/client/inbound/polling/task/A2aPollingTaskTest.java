/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.inbound.polling.task;

import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Artifact;
import io.a2a.spec.Message;
import io.a2a.spec.Task;
import io.a2a.spec.TaskQueryParams;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.a2a.client.common.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aPartToContentConverterImpl;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aSdkObjectConverterImpl;
import io.camunda.connector.agenticai.a2a.client.common.model.A2aConnectionConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aAgentCard;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aArtifact;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTaskStatus;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTaskStatus.TaskState;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClient;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientConfig;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.inbound.polling.model.A2aPollingRuntimeProperties;
import io.camunda.connector.agenticai.a2a.client.inbound.polling.model.A2aPollingRuntimeProperties.A2aPollingRuntimePropertiesData;
import io.camunda.connector.api.inbound.ActivationCheckResult;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.ActivityBuilder;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.inbound.ProcessInstanceContext;
import io.camunda.connector.api.inbound.Severity;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class A2aPollingTaskTest {

  private static final A2aConnectionConfiguration CONNECTION =
      new A2aConnectionConfiguration("http://example.com", null);

  private static final OffsetDateTime WORKING_AT = OffsetDateTime.now().minusMinutes(5);
  private static final OffsetDateTime COMPLETED_AT = OffsetDateTime.now().minusMinutes(3);

  private static final A2aMessage MESSAGE =
      A2aMessage.builder()
          .role(A2aMessage.Role.AGENT)
          .contextId("ctx-123")
          .messageId("msg-123")
          .contents(List.of(textContent("A message")))
          .build();

  private static final A2aTask WORKING_TASK =
      A2aTask.builder()
          .id("task-123")
          .contextId("ctx-123")
          .status(
              A2aTaskStatus.builder()
                  .state(TaskState.WORKING)
                  .message(MESSAGE)
                  .timestamp(WORKING_AT)
                  .build())
          .build();

  private static final A2aTask COMPLETED_TASK =
      WORKING_TASK
          .withStatus(
              WORKING_TASK.status().withState(TaskState.COMPLETED).withTimestamp(COMPLETED_AT))
          .withArtifacts(
              List.of(
                  A2aArtifact.builder()
                      .artifactId("artifact-123")
                      .contents(List.of(textContent("Hello, world")))
                      .build()));

  private static final Message A2A_MESSAGE =
      new Message.Builder()
          .role(Message.Role.AGENT)
          .contextId("ctx-123")
          .messageId("msg-123")
          .parts(List.of(new TextPart("A message")))
          .build();

  private static final Task WORKING_A2A_TASK =
      new Task.Builder()
          .id("task-123")
          .contextId("ctx-123")
          .status(new TaskStatus(io.a2a.spec.TaskState.WORKING, A2A_MESSAGE, WORKING_AT))
          .build();

  private static final Task COMPLETED_A2A_TASK =
      new Task.Builder()
          .id("task-123")
          .contextId("ctx-123")
          .status(new TaskStatus(io.a2a.spec.TaskState.COMPLETED, A2A_MESSAGE, COMPLETED_AT))
          .artifacts(
              List.of(
                  new Artifact.Builder()
                      .artifactId("artifact-123")
                      .parts(new TextPart("Hello, world"))
                      .build()))
          .build();

  @Mock private InboundIntermediateConnectorContext context;
  @Mock private ProcessInstanceContext processInstanceContext;

  @Mock private A2aAgentCardFetcher agentCardFetcher;
  @Mock private A2aSdkClientFactory clientFactory;
  @Mock private A2aSdkClient client;

  private final A2aSdkObjectConverter objectConverter =
      new A2aSdkObjectConverterImpl(new A2aPartToContentConverterImpl());

  @Mock private AgentCard agentCard;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  private List<Activity> loggedActivities;
  private A2aPollingTask pollingTask;

  @BeforeEach
  void setUp() {
    loggedActivities = new ArrayList<>();
    pollingTask =
        new A2aPollingTask(
            context,
            processInstanceContext,
            agentCardFetcher,
            clientFactory,
            objectConverter,
            objectMapper);
  }

  private void expectErrors() {
    doAnswer(
            invocation -> {
              ActivityBuilder builder = Activity.newBuilder();

              Consumer<ActivityBuilder> consumer = invocation.getArgument(0);
              consumer.accept(builder);

              final var activity = builder.build();
              loggedActivities.add(activity);

              return null;
            })
        .when(context)
        .log(ArgumentMatchers.<Consumer<ActivityBuilder>>any());
  }

  @Test
  void returnsWithoutCorrelatingWhenBindingRuntimePropertiesFails() {
    expectErrors();

    doThrow(new RuntimeException("Binding failed"))
        .when(processInstanceContext)
        .bind(A2aPollingRuntimeProperties.class);

    pollingTask.run();

    verify(processInstanceContext, never()).correlate(any());
    assertThat(loggedActivities)
        .hasSize(1)
        .extracting(Activity::severity, Activity::tag, Activity::message)
        .containsExactly(
            tuple(
                Severity.ERROR,
                "a2a-polling-runtime-properties",
                "Failed to bind A2A polling runtime properties: Binding failed"));
  }

  @Test
  void returnsWithoutCorrelatingWhenLoadingClientResponseFails() {
    expectErrors();

    when(processInstanceContext.bind(A2aPollingRuntimeProperties.class))
        .thenReturn(runtimeProperties("invalid"));

    pollingTask.run();

    verify(processInstanceContext, never()).correlate(any());
    assertThat(loggedActivities)
        .hasSize(1)
        .first()
        .satisfies(
            activity -> {
              assertThat(activity.severity()).isEqualTo(Severity.ERROR);
              assertThat(activity.tag()).isEqualTo("a2a-polling-response");
              assertThat(activity.message())
                  .startsWith("Error loading A2A client response: Unrecognized token 'invalid'");
            });
  }

  @Test
  void directlyCorrelatesAgentCard() throws JsonProcessingException {
    final var agentCard =
        A2aAgentCard.builder()
            .id("agent-id")
            .name("agent-name")
            .description("agent-description")
            .build();
    when(processInstanceContext.bind(A2aPollingRuntimeProperties.class))
        .thenReturn(runtimeProperties(objectMapper.writeValueAsString(agentCard)));

    pollingTask.run();

    verify(processInstanceContext)
        .correlate(assertArg(arg -> assertThat(arg).isEqualTo(agentCard)));
    verifyNoInteractions(agentCardFetcher, clientFactory);
  }

  @Test
  void directlyCorrelatesMessage() throws JsonProcessingException {
    when(processInstanceContext.bind(A2aPollingRuntimeProperties.class))
        .thenReturn(runtimeProperties(objectMapper.writeValueAsString(MESSAGE)));

    pollingTask.run();

    verify(processInstanceContext).correlate(assertArg(arg -> assertThat(arg).isEqualTo(MESSAGE)));
    verifyNoInteractions(agentCardFetcher, clientFactory);
  }

  @Test
  void directlyCorrelatesCompletedTask() throws JsonProcessingException {
    final var successfulActivationCheckResult =
        new ActivationCheckResult.Success.CanActivate(mock(ProcessElement.class));
    when(context.canActivate(any(A2aTask.class))).thenReturn(successfulActivationCheckResult);

    when(processInstanceContext.bind(A2aPollingRuntimeProperties.class))
        .thenReturn(runtimeProperties(objectMapper.writeValueAsString(COMPLETED_TASK)));

    pollingTask.run();

    verify(context).canActivate(taskArgumentMatcher(COMPLETED_TASK));
    verify(processInstanceContext).correlate(taskArgumentMatcher(COMPLETED_TASK));
    verifyNoInteractions(agentCardFetcher, clientFactory);
  }

  @Test
  void returnsWithoutCorrelatingWhenLoadingAgentCardFails() throws JsonProcessingException {
    expectErrors();

    when(processInstanceContext.bind(A2aPollingRuntimeProperties.class))
        .thenReturn(runtimeProperties(objectMapper.writeValueAsString(WORKING_TASK)));

    when(agentCardFetcher.fetchAgentCardRaw(CONNECTION))
        .thenThrow(new RuntimeException("Fetching agent card failed"));

    pollingTask.run();

    verify(processInstanceContext, never()).correlate(any());
    assertThat(loggedActivities)
        .hasSize(1)
        .extracting(Activity::severity, Activity::tag, Activity::message)
        .containsExactly(
            tuple(
                Severity.ERROR,
                "a2a-polling-client",
                "Failed to create A2A client: Fetching agent card failed"));
  }

  @Test
  void returnsWithoutCorrelatingWhenCreatingClientFails() throws JsonProcessingException {
    expectErrors();

    when(processInstanceContext.bind(A2aPollingRuntimeProperties.class))
        .thenReturn(runtimeProperties(objectMapper.writeValueAsString(WORKING_TASK)));

    when(agentCardFetcher.fetchAgentCardRaw(CONNECTION)).thenReturn(agentCard);
    when(clientFactory.buildClient(eq(agentCard), any(), eq(a2aClientConfig())))
        .thenThrow(new RuntimeException("Creating client failed"));

    pollingTask.run();

    verify(processInstanceContext, never()).correlate(any());
    assertThat(loggedActivities)
        .hasSize(1)
        .extracting(Activity::severity, Activity::tag, Activity::message)
        .containsExactly(
            tuple(
                Severity.ERROR,
                "a2a-polling-client",
                "Failed to create A2A client: Creating client failed"));
  }

  @Test
  void returnsWithoutCorrelatingWhenLoadingTaskFails() throws JsonProcessingException {
    expectErrors();

    when(processInstanceContext.bind(A2aPollingRuntimeProperties.class))
        .thenReturn(runtimeProperties(objectMapper.writeValueAsString(WORKING_TASK)));

    when(agentCardFetcher.fetchAgentCardRaw(CONNECTION)).thenReturn(agentCard);
    when(clientFactory.buildClient(eq(agentCard), any(), eq(a2aClientConfig()))).thenReturn(client);
    when(client.getTask(new TaskQueryParams(WORKING_TASK.id(), 3)))
        .thenThrow(new RuntimeException("Fetching task failed"));

    pollingTask.run();

    verify(processInstanceContext, never()).correlate(any());
    assertThat(loggedActivities)
        .hasSize(1)
        .extracting(Activity::severity, Activity::tag, Activity::message)
        .containsExactly(
            tuple(
                Severity.ERROR,
                "a2a-polling",
                "Failed to poll A2A task task-123: Fetching task failed"));
  }

  @Test
  void pollsWorkingTask() throws JsonProcessingException {
    when(processInstanceContext.bind(A2aPollingRuntimeProperties.class))
        .thenReturn(runtimeProperties(objectMapper.writeValueAsString(WORKING_TASK)));

    when(agentCardFetcher.fetchAgentCardRaw(CONNECTION)).thenReturn(agentCard);
    when(clientFactory.buildClient(eq(agentCard), any(), eq(a2aClientConfig()))).thenReturn(client);
    when(client.getTask(new TaskQueryParams(WORKING_TASK.id(), 3))).thenReturn(COMPLETED_A2A_TASK);

    pollingTask.run();

    verify(processInstanceContext)
        .correlate(assertArg(arg -> assertThat(arg).isEqualTo(COMPLETED_TASK)));
  }

  @Test
  void createsClientOnlyOnce() throws JsonProcessingException {
    when(processInstanceContext.bind(A2aPollingRuntimeProperties.class))
        .thenReturn(runtimeProperties(objectMapper.writeValueAsString(WORKING_TASK)));

    when(agentCardFetcher.fetchAgentCardRaw(CONNECTION)).thenReturn(agentCard);
    when(clientFactory.buildClient(eq(agentCard), any(), eq(a2aClientConfig()))).thenReturn(client);
    when(client.getTask(new TaskQueryParams(WORKING_TASK.id(), 3))).thenReturn(WORKING_A2A_TASK);

    pollingTask.run();
    pollingTask.run();

    verify(agentCardFetcher, times(1)).fetchAgentCardRaw(any(A2aConnectionConfiguration.class));
    verify(clientFactory, times(1)).buildClient(any(AgentCard.class), any(), any());
    verify(client, times(2)).getTask(any(TaskQueryParams.class));

    verify(processInstanceContext, times(2))
        .correlate(assertArg(arg -> assertThat(arg).isEqualTo(WORKING_TASK)));
  }

  @Test
  void canCloseWithoutInitializedClient() {
    assertThatCode(() -> pollingTask.close()).doesNotThrowAnyException();
    verifyNoInteractions(client);
  }

  @Test
  void closesInitializedClient() throws JsonProcessingException {
    pollsWorkingTask(); // positive task polling case

    pollingTask.close();
    verify(client).close();
  }

  private static A2aSdkClientConfig a2aClientConfig() {
    return new A2aSdkClientConfig(3, null);
  }

  private static A2aPollingRuntimeProperties runtimeProperties(String clientResponse) {
    return new A2aPollingRuntimeProperties(
        new A2aPollingRuntimePropertiesData(CONNECTION, clientResponse, 3));
  }

  private A2aTask taskArgumentMatcher(final A2aTask expectedTask) {
    return assertArg(
        arg ->
            assertThat(arg)
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(OffsetDateTime.class)
                .isEqualTo(expectedTask));
  }
}
