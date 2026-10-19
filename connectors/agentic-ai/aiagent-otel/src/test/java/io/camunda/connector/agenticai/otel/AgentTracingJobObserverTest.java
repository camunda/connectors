/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentFlavor;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobInfo;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobKind;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobObserver;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobFailed;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobStarted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ModelRoundCompleted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ModelRoundFailed;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ModelRoundStarted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ToolDiscoveryDispatched;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ToolExecution;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.TurnCompleted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.TurnStarted;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.otel.configuration.AgentTracingProperties;
import io.camunda.connector.api.outbound.JobCompletionFailure.CommandFailure.CommandIgnored;
import io.camunda.connector.api.outbound.JobCompletionFailure.ExecutionFailed;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentTracingJobObserverTest {

  private static final Instant T0 = Instant.parse("2026-10-19T09:00:00Z");
  private static final long PROCESS_INSTANCE_KEY = 2251799813685249L;
  private static final long AGENT_INSTANCE_KEY = 2251799813685260L;
  private static final String SESSION = "prod-eu:" + AGENT_INSTANCE_KEY;

  private static final ChatModelConfiguration CHAT_MODEL =
      new ChatModelConfiguration() {
        @Override
        public String provider() {
          return "anthropic";
        }

        @Override
        public String model() {
          return "claude-x";
        }
      };

  private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
  private SdkTracerProvider tracerProvider;
  private AgentTracingListener listener;

  @BeforeEach
  void setUp() {
    tracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
    final var openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
    listener =
        new AgentTracingListener(
            new AgentTracerImpl(openTelemetry), new AgentTracingProperties(true, false, "prod-eu"));
  }

  @AfterEach
  void tearDown() {
    tracerProvider.close();
  }

  @Test
  void tracesARunAcrossJobsAndEmitsToolSpansWithTheirRealTimes() {
    // job 1: tool discovery starts the run
    final var discovery =
        listener.onJobStarted(started(AgentJobKind.TOOL_DISCOVERY, T0, null, Map.of()));
    final var afterDiscovery = discovery.onToolDiscoveryDispatched(new ToolDiscoveryDispatched(1));
    discovery.onJobCompleted();

    assertThat(afterDiscovery).containsEntry("status", "OPEN").containsEntry("iterationKey", 0);
    final SpanData anchor = span("agent run");
    final SpanData discoveryJob = span("invoke_agent agent");
    assertThat(anchor.getParentSpanContext().isValid()).isFalse();
    assertThat(anchor.getAttributes().get(GenAiAttributes.OPERATION_NAME)).isNull();
    assertThat(discoveryJob.getParentSpanId()).isEqualTo(anchor.getSpanId());
    assertThat(discoveryJob.getAttributes().get(GenAiAttributes.SESSION_ID)).isEqualTo(SESSION);
    exporter.reset();

    // job 2: the first turn issues a tool call and persists the next state
    final var firstTurn =
        listener.onJobStarted(
            started(AgentJobKind.CONVERSATION, T0.plusSeconds(1), null, afterDiscovery));
    firstTurn.onTurnStarted(turnStarted(1, List.of(), null));
    chatRound(firstTurn, StopReason.TOOL_USE);
    final var afterFirstTurn = firstTurn.onTurnCompleted(turnCompleted(1, true));
    firstTurn.onJobCompleted();

    assertThat(afterFirstTurn).containsEntry("status", "OPEN").containsEntry("iterationKey", 1);
    assertThat(afterFirstTurn)
        .containsEntry("traceId", anchor.getTraceId())
        .containsEntry("anchorSpanId", anchor.getSpanId());
    assertThat(exporter.getFinishedSpanItems()).hasSize(2); // chat and invoke_agent, no anchor
    assertThat(span("chat claude-x").getAttributes().get(GenAiAttributes.USAGE_INPUT_TOKENS))
        .isEqualTo(15L);
    assertThat(span("invoke_agent agent").getParentSpanId()).isEqualTo(anchor.getSpanId());
    exporter.reset();

    // job 4, two days later: consumes two tool results and answers
    final Instant issuedAt = T0.plusSeconds(6);
    final Instant completedA = T0.plusSeconds(8);
    final Instant completedB = T0.plusSeconds(2 * 24 * 3600);
    final Instant job4Start = T0.plusSeconds(2 * 24 * 3600 + 1);
    final var lastTurn =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, job4Start, 1, afterFirstTurn));
    lastTurn.onTurnStarted(
        turnStarted(
            2,
            List.of(
                toolExecution("call-1", "search", completedA, false),
                toolExecution("call-2", "approval", completedB, true)),
            issuedAt));
    chatRound(lastTurn, StopReason.STOP);
    final var afterLastTurn = lastTurn.onTurnCompleted(turnCompleted(2, false));
    assertThat(exporter.getFinishedSpanItems()).hasSize(1); // chat ended, nothing else yet
    lastTurn.onJobCompleted();

    assertThat(afterLastTurn).containsEntry("status", "CLOSED").containsEntry("iterationKey", 2);
    assertThat(exporter.getFinishedSpanItems())
        .hasSize(4)
        .allSatisfy(span -> assertThat(span.getTraceId()).isEqualTo(anchor.getTraceId()));
    final SpanData invokeAgent = span("invoke_agent agent");
    assertThat(invokeAgent.getStartEpochNanos()).isEqualTo(nanos(job4Start));
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.JOB_OUTCOME)).isEqualTo("completed");
    final SpanData searchTool = span("execute_tool search");
    assertThat(searchTool.getStartEpochNanos()).isEqualTo(nanos(issuedAt));
    assertThat(searchTool.getEndEpochNanos()).isEqualTo(nanos(completedA));
    assertThat(searchTool.getParentSpanId()).isEqualTo(invokeAgent.getSpanId());
    assertThat(searchTool.getAttributes().get(GenAiAttributes.TOOL_OUTCOME)).isEqualTo("completed");
    final SpanData approvalTool = span("execute_tool approval");
    assertThat(approvalTool.getEndEpochNanos()).isEqualTo(nanos(completedB));
    assertThat(approvalTool.getAttributes().get(GenAiAttributes.TOOL_OUTCOME))
        .isEqualTo("cancelled");
    assertThat(approvalTool.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
    assertThat(approvalTool.getAttributes().get(GenAiAttributes.TOOL_CALL_RESULT)).isNull();
    exporter.reset();

    // the next input starts a new run in the same session, linked to the first anchor
    final var followUp =
        listener.onJobStarted(
            started(AgentJobKind.CONVERSATION, job4Start.plusSeconds(1800), 2, afterLastTurn));
    followUp.onTurnStarted(turnStarted(3, List.of(), null));
    followUp.onTurnCompleted(turnCompleted(3, false));
    followUp.onJobCompleted();

    final SpanData secondAnchor = span("agent run");
    assertThat(secondAnchor.getTraceId()).isNotEqualTo(anchor.getTraceId());
    assertThat(secondAnchor.getLinks()).hasSize(1);
    assertThat(secondAnchor.getLinks().get(0).getSpanContext().getSpanId())
        .isEqualTo(anchor.getSpanId());
    assertThat(secondAnchor.getAttributes().get(GenAiAttributes.SESSION_ID)).isEqualTo(SESSION);
  }

  @Test
  void failedRunStartingJobEndsItsSpansOnceAsFailedWithoutMessages() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));
    final var failure = new IllegalStateException("prompt text that must not leak");

    observer.onTurnStarted(turnStarted(1, List.of(), null));
    observer.onModelRoundStarted(roundStarted());
    observer.onModelRoundFailed(
        new ModelRoundFailed("MODEL_CONTEXT_WINDOW_EXCEEDED", null, failure));
    observer.onJobFailed(new JobFailed(failure, "MODEL_CONTEXT_WINDOW_EXCEEDED"));
    // the SDK reports the failed completion afterwards; the first end wins
    observer.onJobCompletionFailed(new ExecutionFailed(failure, null));
    observer.onJobCompleted();

    assertThat(exporter.getFinishedSpanItems()).hasSize(3);
    final SpanData chat = span("chat claude-x");
    assertThat(chat.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(chat.getStatus().getDescription()).isEmpty();
    assertThat(chat.getAttributes().get(GenAiAttributes.ERROR_TYPE))
        .isEqualTo("MODEL_CONTEXT_WINDOW_EXCEEDED");
    assertThat(chat.getEvents()).isEmpty();
    final SpanData invokeAgent = span("invoke_agent agent");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.JOB_OUTCOME)).isEqualTo("failed");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.ERROR_TYPE))
        .isEqualTo("MODEL_CONTEXT_WINDOW_EXCEEDED");
    assertThat(invokeAgent.getStatus().getDescription()).isEmpty();
    final SpanData anchor = span("agent run");
    assertThat(anchor.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
  }

  @Test
  void supersededJobKeepsItsSpansButEmitsNoToolSpans() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, 1, openState(1)));
    observer.onTurnStarted(
        turnStarted(2, List.of(toolExecution("call-1", "search", T0.plusSeconds(5), false)), T0));
    observer.onTurnCompleted(turnCompleted(2, false));
    observer.onJobCompletionFailed(new CommandIgnored(new RuntimeException("not found")));

    assertThat(exporter.getFinishedSpanItems()).hasSize(1);
    final SpanData invokeAgent = span("invoke_agent agent");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.JOB_OUTCOME))
        .isEqualTo("superseded");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.ERROR_TYPE))
        .isEqualTo("job_superseded");
  }

  @Test
  void deferredAndUntracedJobsEmitNothing() {
    final var deferred =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, 1, openState(1)));
    deferred.onJobCompleted();

    final var withoutAgentInstance =
        listener.onJobStarted(
            new JobStarted(
                jobInfo(),
                AgentFlavor.SUB_PROCESS,
                AgentJobKind.CONVERSATION,
                T0,
                null,
                null,
                Map.of()));
    assertThat(withoutAgentInstance).isSameAs(AgentJobObserver.NOOP);
    assertThat(exporter.getFinishedSpanItems()).isEmpty();
  }

  // --- helpers ---

  private Map<String, Object> openState(int iterationKey) {
    return RunTraceState.of(
            "4bf92f3577b34da6a3ce929d0e0e4736",
            "00f067aa0ba902b7",
            true,
            RunTraceState.Status.OPEN,
            iterationKey,
            PROCESS_INSTANCE_KEY)
        .toMap();
  }

  private static void chatRound(AgentJobObserver observer, StopReason stopReason) {
    observer.onModelRoundStarted(roundStarted());
    observer.onModelRoundCompleted(
        new ModelRoundCompleted(
            AssistantMessage.builder()
                .modelId("claude-x-20261019")
                .messageId("msg_1")
                .stopReason(stopReason)
                .build(),
            new AgentMetrics(1, new TokenUsage(10, 5, 5, 0, 0), 0),
            false));
  }

  private static ModelRoundStarted roundStarted() {
    return new ModelRoundStarted(1, CHAT_MODEL, List.of(), List.of());
  }

  private static TurnStarted turnStarted(
      int iterationKey, List<ToolExecution> executions, @Nullable Instant issuedAt) {
    final List<Message> input =
        List.of(UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build());
    return new TurnStarted(iterationKey, executions, issuedAt, input, null, CHAT_MODEL, List.of());
  }

  private static TurnCompleted turnCompleted(int iterationKey, boolean withToolCalls) {
    return new TurnCompleted(
        iterationKey,
        withToolCalls ? List.of(new ToolCall("call-1", "search", Map.of())) : List.of(),
        null,
        AgentMetrics.empty());
  }

  private static ToolExecution toolExecution(
      String id, String name, Instant completedAt, boolean cancelled) {
    return new ToolExecution(
        new ToolCall(id, name, Map.of("q", "x")),
        ToolCallResultContent.builder()
            .id(id)
            .name(name)
            .elementId("Task_" + name)
            .content(List.of(TextContent.textContent("result")))
            .completedAt(OffsetDateTime.ofInstant(completedAt, ZoneOffset.UTC))
            .build(),
        cancelled);
  }

  private static AgentJobInfo jobInfo() {
    return new AgentJobInfo(7, PROCESS_INSTANCE_KEY, 3, 4, "agent", "process", "<default>", "ab12");
  }

  private static JobStarted started(
      AgentJobKind kind,
      Instant startedAt,
      @Nullable Integer lastIterationKey,
      Map<String, Object> previousState) {
    return new JobStarted(
        jobInfo(),
        AgentFlavor.SUB_PROCESS,
        kind,
        startedAt,
        AGENT_INSTANCE_KEY,
        lastIterationKey,
        previousState);
  }

  private SpanData span(String name) {
    return exporter.getFinishedSpanItems().stream()
        .filter(span -> span.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no span " + name));
  }

  private static long nanos(Instant instant) {
    return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
  }
}
