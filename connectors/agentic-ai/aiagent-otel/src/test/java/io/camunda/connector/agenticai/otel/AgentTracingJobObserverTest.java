/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.otel.configuration.AgentTracingProperties;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobCompletionFailure.CommandFailure.CommandIgnored;
import io.camunda.connector.api.outbound.JobCompletionFailure.ExecutionFailed;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

  private final MutableClock clock = new MutableClock(T0);
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
            new AgentTracer(openTelemetry),
            new AgentTracingProperties(true, false, "prod-eu"),
            clock);
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
    final var afterDiscovery =
        discovery.onToolDiscoveryDispatched(new ToolDiscoveryDispatched(List.of()));
    discovery.onJobCompleted();

    final SpanData anchor = span("agent run");
    final SpanData discoveryJob = span("invoke_agent agent");
    assertThat(afterDiscovery)
        .containsEntry("status", "OPEN")
        .containsEntry("iterationKey", 0)
        .containsEntry("issuerSpanId", discoveryJob.getSpanId());
    assertThat(anchor.getParentSpanContext().isValid()).isFalse();
    assertThat(anchor.getAttributes().get(GenAiAttributes.OPERATION_NAME)).isNull();
    assertThat(discoveryJob.getParentSpanId()).isEqualTo(anchor.getSpanId());
    assertThat(discoveryJob.getAttributes().get(GenAiAttributes.SESSION_ID)).isEqualTo(SESSION);
    exporter.reset();

    // job 2: the first turn issues a tool call and persists the next state
    final var firstTurn =
        listener.onJobStarted(
            started(AgentJobKind.CONVERSATION, T0.plusSeconds(1), null, afterDiscovery));
    firstTurn.onTurnStarted(turnStarted(1, List.of()));
    chatRound(firstTurn, StopReason.TOOL_USE);
    clock.set(T0.plusSeconds(5));
    final var afterFirstTurn = firstTurn.onTurnCompleted(turnCompleted(1, true));
    firstTurn.onJobCompleted();

    assertThat(afterFirstTurn).containsEntry("status", "OPEN").containsEntry("iterationKey", 1);
    assertThat(afterFirstTurn)
        .containsEntry("traceId", anchor.getTraceId())
        .containsEntry("anchorSpanId", anchor.getSpanId());
    assertThat(exporter.getFinishedSpanItems()).hasSize(2); // chat and invoke_agent, no anchor
    assertThat(span("chat claude-x").getAttributes().get(GenAiAttributes.USAGE_INPUT_TOKENS))
        .isEqualTo(15L);
    final SpanData issuingJob = span("invoke_agent agent");
    assertThat(issuingJob.getParentSpanId()).isEqualTo(anchor.getSpanId());
    assertThat(afterFirstTurn).containsEntry("issuerSpanId", issuingJob.getSpanId());
    assertThat(issuedAtOf(afterFirstTurn)).isEqualTo(T0.plusSeconds(5));
    exporter.reset();

    // job 3: only some tool results arrived, the job exports nothing and persists nothing
    final var deferred =
        listener.onJobStarted(
            started(AgentJobKind.CONVERSATION, T0.plusSeconds(9), 1, afterFirstTurn));
    deferred.onJobCompleted();
    assertThat(exporter.getFinishedSpanItems()).isEmpty();

    // job 4, two days later: consumes two tool results and answers; the state it reads holds the
    // dispatch time the issuing job recorded
    final Instant issuedAt = T0.plusSeconds(6);
    final Map<String, Object> issuedStateFromJob2 = withIssuedAt(afterFirstTurn, issuedAt);
    final Instant completedA = T0.plusSeconds(8);
    final Instant completedB = T0.plusSeconds(2 * 24 * 3600);
    final Instant job4Start = T0.plusSeconds(2 * 24 * 3600 + 1);
    final var lastTurn =
        listener.onJobStarted(
            started(AgentJobKind.CONVERSATION, job4Start, 1, issuedStateFromJob2));
    lastTurn.onTurnStarted(
        turnStarted(
            2,
            List.of(
                toolExecution("call-1", "search", completedA, false),
                toolExecution("call-2", "approval", completedB, true))));
    chatRound(lastTurn, StopReason.STOP);
    final var afterLastTurn = lastTurn.onTurnCompleted(turnCompleted(2, false));
    assertThat(exporter.getFinishedSpanItems()).hasSize(1); // chat ended, nothing else yet
    lastTurn.onJobCompleted();

    assertThat(afterLastTurn).containsEntry("status", "CLOSED").containsEntry("iterationKey", 2);
    assertThat(afterLastTurn).containsEntry("issuerSpanId", span("invoke_agent agent").getSpanId());
    assertThat(afterLastTurn).doesNotContainKey("issuedAt");
    assertThat(exporter.getFinishedSpanItems())
        .hasSize(4)
        .allSatisfy(span -> assertThat(span.getTraceId()).isEqualTo(anchor.getTraceId()));
    final SpanData invokeAgent = span("invoke_agent agent");
    assertThat(invokeAgent.getStartEpochNanos()).isEqualTo(nanos(job4Start));
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.JOB_OUTCOME)).isEqualTo("completed");
    final SpanData searchTool = span("execute_tool search");
    assertThat(searchTool.getStartEpochNanos()).isEqualTo(nanos(issuedAt));
    assertThat(searchTool.getEndEpochNanos()).isEqualTo(nanos(completedA));
    assertThat(searchTool.getParentSpanId()).isEqualTo(issuingJob.getSpanId());
    assertThat(searchTool.getStartEpochNanos())
        .isGreaterThanOrEqualTo(issuingJob.getStartEpochNanos());
    assertThat(searchTool.getAttributes().get(GenAiAttributes.TOOL_OUTCOME)).isEqualTo("completed");
    assertThat(searchTool.getAttributes().asMap().keySet())
        .extracting(Object::toString)
        .doesNotContain("camunda.agent.tool.start_time_source");
    assertThat(searchTool.getAttributes().get(GenAiAttributes.AGENT_NAME)).isEqualTo("agent");
    assertThat(searchTool.getAttributes().get(GenAiAttributes.TOOL_DESCRIPTION)).isNull();
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.PROCESS_DEFINITION_KEY))
        .isEqualTo("3");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.INPUT_MESSAGES)).isNull();
    final SpanData approvalTool = span("execute_tool approval");
    assertThat(approvalTool.getParentSpanId()).isEqualTo(issuingJob.getSpanId());
    assertThat(approvalTool.getStartEpochNanos()).isEqualTo(nanos(issuedAt));
    assertThat(approvalTool.getEndEpochNanos()).isEqualTo(nanos(completedB));
    assertThat(approvalTool.getAttributes().get(GenAiAttributes.TOOL_OUTCOME))
        .isEqualTo("cancelled");
    assertThat(approvalTool.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
    assertThat(approvalTool.getAttributes().get(GenAiAttributes.TOOL_CALL_RESULT)).isNull();
    exporter.reset();

    // the next input starts a new run in the same session
    final var followUp =
        listener.onJobStarted(
            started(AgentJobKind.CONVERSATION, job4Start.plusSeconds(1800), 2, afterLastTurn));
    followUp.onTurnStarted(turnStarted(3, List.of()));
    chatRound(followUp, StopReason.STOP);
    followUp.onTurnCompleted(turnCompleted(3, false));
    followUp.onJobCompleted();

    final SpanData secondAnchor = span("agent run");
    assertThat(secondAnchor.getTraceId()).isNotEqualTo(anchor.getTraceId());
    assertThat(span("invoke_agent agent").getParentSpanId()).isEqualTo(secondAnchor.getSpanId());
    assertThat(secondAnchor.getLinks()).isEmpty();
    assertThat(secondAnchor.getAttributes().get(GenAiAttributes.SESSION_ID)).isEqualTo(SESSION);
  }

  @Test
  void failedRunStartingJobEndsItsSpansOnceAsFailedWithoutMessages() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));
    final var failure =
        new ConnectorException("MODEL_CONTEXT_WINDOW_EXCEEDED", "prompt text that must not leak");

    observer.onTurnStarted(turnStarted(1, List.of()));
    observer.onModelRoundStarted(roundStarted());
    observer.onModelRoundFailed(new ModelRoundFailed(failure, "MODEL_CONTEXT_WINDOW_EXCEEDED"));
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
    assertThat(chat.getAttributes().get(GenAiAttributes.RESPONSE_FINISH_REASONS))
        .containsExactly("error");
    final SpanData invokeAgent = span("invoke_agent agent");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.JOB_OUTCOME)).isEqualTo("failed");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.ERROR_TYPE))
        .isEqualTo("MODEL_CONTEXT_WINDOW_EXCEEDED");
    assertThat(invokeAgent.getStatus().getDescription()).isEmpty();
    final SpanData anchor = span("agent run");
    assertThat(anchor.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
    assertThat(anchor.getAttributes().get(GenAiAttributes.ERROR_TYPE)).isNull();
    assertThat(anchor.getAttributes().get(GenAiAttributes.JOB_OUTCOME)).isNull();
  }

  @Test
  void failedModelRoundIsTypedByTheProviderExceptionBehindTheConnectorWrapper() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));
    final var providerFailure = new java.net.SocketTimeoutException("prompt text");
    final var failure = new ConnectorException("FAILED_MODEL_CALL", "prompt text", providerFailure);

    observer.onTurnStarted(turnStarted(1, List.of()));
    observer.onModelRoundStarted(roundStarted());
    observer.onModelRoundFailed(new ModelRoundFailed(failure, "FAILED_MODEL_CALL"));
    observer.onJobFailed(new JobFailed(failure, "FAILED_MODEL_CALL"));

    assertThat(span("chat claude-x").getAttributes().get(GenAiAttributes.ERROR_TYPE))
        .isEqualTo("java.net.SocketTimeoutException");
    assertThat(span("invoke_agent agent").getAttributes().get(GenAiAttributes.ERROR_TYPE))
        .isEqualTo("java.net.SocketTimeoutException");
    assertThat(span("chat claude-x").getStatus().getDescription()).isEmpty();
  }

  @Test
  void chatSpanStillOpenWhenTheJobEndsIsEndedAsFailed() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));

    observer.onTurnStarted(turnStarted(1, List.of()));
    observer.onModelRoundStarted(roundStarted());
    observer.onJobCompletionFailed(new CommandIgnored(new RuntimeException("not found")));

    final SpanData chat = span("chat claude-x");
    assertThat(chat.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(chat.getAttributes().get(GenAiAttributes.ERROR_TYPE)).isEqualTo("job_superseded");
    assertThat(chat.getAttributes().get(GenAiAttributes.RESPONSE_FINISH_REASONS))
        .containsExactly("error");
  }

  @Test
  void agentSpanCarriesTheTurnMessagesOnlyWithContentCapture() {
    final var capturing =
        new AgentTracingListener(
            new AgentTracer(OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()),
            new AgentTracingProperties(true, true, "prod-eu"),
            clock);
    final var observer =
        capturing.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));

    observer.onTurnStarted(turnStarted(1, List.of()));
    observer.onTurnCompleted(
        new TurnCompleted(
            1,
            List.of(),
            AssistantMessage.builder().content(List.of(TextContent.textContent("answer"))).build(),
            AgentMetrics.empty()));
    observer.onJobCompleted();

    final SpanData invokeAgent = span("invoke_agent agent");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.INPUT_MESSAGES))
        .contains("\"role\":\"user\"", "hi");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.OUTPUT_MESSAGES))
        .contains("\"role\":\"assistant\"", "answer");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.USAGE_INPUT_TOKENS)).isNull();
  }

  @Test
  void anchorShowsTheRunInputInNameAndAttributesOnlyWithContentCapture() {
    final var capturing =
        new AgentTracingListener(
            new AgentTracer(OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()),
            new AgentTracingProperties(true, true, null),
            clock);
    final var observer =
        capturing.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));
    observer.onTurnStarted(turnStarted(1, List.of()));
    chatRound(observer, StopReason.STOP);
    observer.onTurnCompleted(turnCompleted(1, false));
    observer.onJobCompleted();

    final SpanData anchor = span("agent run: hi");
    assertThat(anchor.getAttributes().get(GenAiAttributes.INPUT_VALUE)).isEqualTo("hi");
    assertThat(anchor.getAttributes().get(GenAiAttributes.OPENINFERENCE_SPAN_KIND))
        .isEqualTo("AGENT");
    exporter.reset();

    final var plain = listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));
    plain.onTurnStarted(turnStarted(1, List.of()));
    chatRound(plain, StopReason.STOP);
    plain.onTurnCompleted(turnCompleted(1, false));
    plain.onJobCompleted();

    final SpanData plainAnchor = span("agent run");
    assertThat(plainAnchor.getAttributes().get(GenAiAttributes.INPUT_VALUE)).isNull();
    assertThat(plainAnchor.getAttributes().get(GenAiAttributes.OPENINFERENCE_SPAN_KIND)).isNull();
  }

  @Test
  void anchorOfADiscoveryFirstRunStaysPlainEvenWithContentCapture() {
    final var capturing =
        new AgentTracingListener(
            new AgentTracer(OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()),
            new AgentTracingProperties(true, true, null),
            clock);
    final var observer =
        capturing.onJobStarted(started(AgentJobKind.TOOL_DISCOVERY, T0, null, Map.of()));

    observer.onToolDiscoveryDispatched(new ToolDiscoveryDispatched(List.of()));
    observer.onJobCompleted();

    assertThat(span("agent run").getAttributes().get(GenAiAttributes.INPUT_VALUE)).isNull();
  }

  @Test
  void supersededJobCompletionKeepsItsSpansWithoutFailureStatusAndEmitsNoToolSpans() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, 1, openState(1)));
    observer.onTurnStarted(
        turnStarted(2, List.of(toolExecution("call-1", "search", T0.plusSeconds(5), false))));
    chatRound(observer, StopReason.STOP);
    observer.onTurnCompleted(turnCompleted(2, false));
    observer.onJobCompletionFailed(new CommandIgnored(new RuntimeException("not found")));

    assertThat(exporter.getFinishedSpanItems()).hasSize(2);
    final SpanData invokeAgent = span("invoke_agent agent");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.JOB_OUTCOME))
        .isEqualTo("superseded");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.ERROR_TYPE))
        .isEqualTo("job_superseded");
    assertThat(invokeAgent.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
  }

  @Test
  void jobSupersededByTheFencedWriteAfterAModelRoundExportsAsSupersededNotAsFailed() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));
    final var failure = new ConnectorException("AGENT_INSTANCE_SUPERSEDED", "job superseded");

    observer.onTurnStarted(turnStarted(1, List.of()));
    chatRound(observer, StopReason.TOOL_USE);
    observer.onJobFailed(new JobFailed(failure, "AGENT_INSTANCE_SUPERSEDED"));

    assertThat(exporter.getFinishedSpanItems()).hasSize(3);
    final SpanData invokeAgent = span("invoke_agent agent");
    assertThat(invokeAgent.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.ERROR_TYPE))
        .isEqualTo("job_superseded");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.JOB_OUTCOME))
        .isEqualTo("superseded");
    final SpanData anchor = span("agent run");
    assertThat(anchor.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
    assertThat(anchor.getAttributes().get(GenAiAttributes.ERROR_TYPE)).isNull();
    assertThat(anchor.getAttributes().get(GenAiAttributes.JOB_OUTCOME)).isNull();
  }

  @Test
  void jobSupersededBeforeItsTurnStartedExportsNothing() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));

    observer.onJobFailed(
        new JobFailed(
            new ConnectorException("AGENT_INSTANCE_SUPERSEDED", "job superseded"),
            "AGENT_INSTANCE_SUPERSEDED"));
    observer.onJobCompletionFailed(new ExecutionFailed(new RuntimeException("again"), null));

    assertThat(exporter.getFinishedSpanItems()).isEmpty();
  }

  @Test
  void toolDiscoveryJobSupersededBeforeItsDispatchExportsNothing() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.TOOL_DISCOVERY, T0, null, Map.of()));

    observer.onJobFailed(
        new JobFailed(
            new ConnectorException("AGENT_INSTANCE_SUPERSEDED", "job superseded"),
            "AGENT_INSTANCE_SUPERSEDED"));
    observer.onJobCompletionFailed(new ExecutionFailed(new RuntimeException("again"), null));

    assertThat(exporter.getFinishedSpanItems()).isEmpty();
  }

  @Test
  void toolDiscoveryJobSupersededAfterItsDispatchExportsAsSuperseded() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.TOOL_DISCOVERY, T0, null, Map.of()));

    observer.onToolDiscoveryDispatched(new ToolDiscoveryDispatched(List.of()));
    observer.onJobCompletionFailed(new CommandIgnored(new RuntimeException("not found")));

    assertThat(exporter.getFinishedSpanItems())
        .extracting(SpanData::getName)
        .containsExactlyInAnyOrder("invoke_agent agent", "agent run");
    final SpanData invokeAgent = span("invoke_agent agent");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.JOB_OUTCOME))
        .isEqualTo("superseded");
    assertThat(invokeAgent.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
  }

  @Test
  void jobFailingBeforeItsFirstModelRoundExportsItsFailedSpans() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));
    final var failure = new ConnectorException("MAX_MODEL_CALLS_EXCEEDED", "limit");

    observer.onTurnStarted(turnStarted(1, List.of()));
    observer.onJobFailed(new JobFailed(failure, "MAX_MODEL_CALLS_EXCEEDED"));

    assertThat(exporter.getFinishedSpanItems())
        .extracting(SpanData::getName)
        .containsExactlyInAnyOrder("invoke_agent agent", "agent run");
    final SpanData invokeAgent = span("invoke_agent agent");
    assertThat(invokeAgent.getStartEpochNanos()).isEqualTo(nanos(T0));
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.JOB_OUTCOME)).isEqualTo("failed");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.ERROR_TYPE))
        .isEqualTo("MAX_MODEL_CALLS_EXCEEDED");
    assertThat(invokeAgent.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
  }

  @Test
  void runStartingJobFailingBeforeItsTurnStartedExportsItsAnchorAndFailedSpan() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));

    observer.onJobFailed(
        new JobFailed(
            new ConnectorException("MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED", "limit"),
            "MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED"));
    observer.onJobCompletionFailed(new ExecutionFailed(new RuntimeException("again"), null));

    assertThat(exporter.getFinishedSpanItems())
        .extracting(SpanData::getName)
        .containsExactlyInAnyOrder("invoke_agent agent", "agent run");
    final SpanData anchor = span("agent run");
    final SpanData invokeAgent = span("invoke_agent agent");
    assertThat(invokeAgent.getParentSpanId()).isEqualTo(anchor.getSpanId());
    assertThat(invokeAgent.getStartEpochNanos()).isEqualTo(nanos(T0));
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.JOB_OUTCOME)).isEqualTo("failed");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.ERROR_TYPE))
        .isEqualTo("MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED");
    assertThat(invokeAgent.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(anchor.getEndEpochNanos()).isEqualTo(nanos(T0));
  }

  @Test
  void jobFailingBeforeAnyTurnStartedExportsItsFailedSpans() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, 1, openState(1)));

    observer.onJobFailed(new JobFailed(new IllegalStateException("tool resolution"), null));

    final SpanData invokeAgent = span("invoke_agent agent");
    assertThat(invokeAgent.getAttributes().get(GenAiAttributes.ERROR_TYPE))
        .isEqualTo("java.lang.IllegalStateException");
    assertThat(exporter.getFinishedSpanItems()).hasSize(1); // continues the run, no anchor
  }

  @Test
  void spansStartAtTheTurnStartWithTheRecordedJobStart() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));

    observer.onTurnStarted(turnStarted(1, List.of()));
    chatRound(observer, StopReason.STOP);
    final var state = observer.onTurnCompleted(turnCompleted(1, false));
    observer.onJobCompleted();

    assertThat(state).containsEntry("status", "CLOSED");
    assertThat(span("agent run").getStartEpochNanos()).isEqualTo(nanos(T0));
    assertThat(span("invoke_agent agent").getStartEpochNanos()).isEqualTo(nanos(T0));
  }

  @Test
  void anchorIsAZeroDurationMarkerAtTheStartOfTheRunStartingJob() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));

    observer.onTurnStarted(turnStarted(1, List.of()));
    // the marker is exported as soon as the turn starts, whatever the job does next
    assertThat(exporter.getFinishedSpanItems())
        .extracting(SpanData::getName)
        .containsExactly("agent run");

    assertThat(span("agent run").getEndEpochNanos()).isEqualTo(nanos(T0));
    observer.onModelRoundStarted(roundStarted());
    clock.set(T0.plusSeconds(3));
    observer.onJobCompleted();

    final SpanData anchor = span("agent run");
    final SpanData invokeAgent = span("invoke_agent agent");
    assertThat(anchor.getStartEpochNanos())
        .isEqualTo(anchor.getEndEpochNanos())
        .isEqualTo(invokeAgent.getStartEpochNanos())
        .isEqualTo(nanos(T0));
    assertThat(invokeAgent.getEndEpochNanos()).isEqualTo(nanos(T0.plusSeconds(3)));
  }

  @Test
  void chatSpansStartAndEndAtTheTimesOfTheClock() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));
    observer.onTurnStarted(turnStarted(1, List.of()));

    clock.set(T0.plusSeconds(2));
    observer.onModelRoundStarted(roundStarted());
    clock.set(T0.plusSeconds(5));
    observer.onModelRoundFailed(new ModelRoundFailed(new IllegalStateException("boom"), null));

    clock.set(T0.plusSeconds(6));
    observer.onModelRoundStarted(roundStarted());
    clock.set(T0.plusSeconds(8));
    observer.onModelRoundCompleted(
        new ModelRoundCompleted(
            AssistantMessage.builder().modelId("claude-x").messageId("msg_1").build(),
            new AgentMetrics(1, new TokenUsage(10, 5, 5, 0, 0), 0),
            false));

    clock.set(T0.plusSeconds(9));
    observer.onModelRoundStarted(roundStarted());
    clock.set(T0.plusSeconds(11));
    observer.onJobFailed(new JobFailed(new IllegalStateException("boom"), null));

    assertThat(exporter.getFinishedSpanItems())
        .filteredOn(span -> span.getName().equals("chat claude-x"))
        .extracting(SpanData::getStartEpochNanos, SpanData::getEndEpochNanos)
        .containsExactly(
            tuple(nanos(T0.plusSeconds(2)), nanos(T0.plusSeconds(5))),
            tuple(nanos(T0.plusSeconds(6)), nanos(T0.plusSeconds(8))),
            tuple(nanos(T0.plusSeconds(9)), nanos(T0.plusSeconds(11))));
    assertThat(span("invoke_agent agent").getEndEpochNanos()).isEqualTo(nanos(T0.plusSeconds(11)));
  }

  @Test
  void anchorOfADiscoveryFirstRunIsAZeroDurationMarker() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.TOOL_DISCOVERY, T0, null, Map.of()));

    observer.onToolDiscoveryDispatched(new ToolDiscoveryDispatched(List.of()));
    observer.onJobCompleted();

    final SpanData anchor = span("agent run");
    assertThat(anchor.getStartEpochNanos())
        .isEqualTo(anchor.getEndEpochNanos())
        .isEqualTo(span("invoke_agent agent").getStartEpochNanos())
        .isEqualTo(nanos(T0));
  }

  @Test
  void aJobContinuingARunExportsNoSpanBeforeItsJobEnds() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, 1, openState(1)));

    observer.onTurnStarted(turnStarted(2, List.of()));
    observer.onModelRoundStarted(roundStarted());

    assertThat(exporter.getFinishedSpanItems()).isEmpty();
  }

  @Test
  void aFailingSpanDecorationNeverKeepsAnotherSpanFromEnding() {
    final var failingTracer =
        new AgentTracer(OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()) {
          @Override
          public void markError(Span span, String errorType, @Nullable String description) {
            throw new IllegalStateException("decoration failed");
          }
        };
    final var observer =
        new AgentTracingListener(
                failingTracer, new AgentTracingProperties(true, false, null), clock)
            .onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));

    observer.onTurnStarted(turnStarted(1, List.of()));
    observer.onModelRoundStarted(roundStarted());
    observer.onJobFailed(new JobFailed(new IllegalStateException("boom"), null));

    assertThat(exporter.getFinishedSpanItems())
        .extracting(SpanData::getName)
        .containsExactlyInAnyOrder("chat claude-x", "invoke_agent agent", "agent run");
  }

  @Test
  void toolDescriptionIsRecordedOnlyWithContentCapture() {
    final var capturing =
        new AgentTracingListener(
            new AgentTracer(OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()),
            new AgentTracingProperties(true, true, null),
            clock);
    final var observer =
        capturing.onJobStarted(started(AgentJobKind.CONVERSATION, T0, 1, openState(1)));

    observer.onTurnStarted(
        turnStarted(2, List.of(toolExecution("call-1", "search", T0.plusSeconds(5), false))));
    chatRound(observer, StopReason.STOP);
    observer.onJobCompleted();

    assertThat(span("execute_tool search").getAttributes().get(GenAiAttributes.TOOL_DESCRIPTION))
        .isEqualTo("Search the web");
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

  @Test
  void toolSpansOfADiscoveryIssuedCallAreParentedUnderTheDiscoveryJob() {
    final var discovery =
        listener.onJobStarted(started(AgentJobKind.TOOL_DISCOVERY, T0, null, Map.of()));
    final var afterDiscovery =
        discovery.onToolDiscoveryDispatched(new ToolDiscoveryDispatched(List.of()));
    discovery.onJobCompleted();
    final SpanData discoveryJob = span("invoke_agent agent");
    exporter.reset();

    assertThat(afterDiscovery).doesNotContainKey("issuedAt");
    final var consuming =
        listener.onJobStarted(
            started(AgentJobKind.CONVERSATION, T0.plusSeconds(30), 0, afterDiscovery));
    consuming.onTurnStarted(
        turnStarted(1, List.of(toolExecution("call-1", "search", T0.plusSeconds(25), false))));
    chatRound(consuming, StopReason.STOP);
    consuming.onJobCompleted();

    final SpanData consumingJob = span("invoke_agent agent");
    final SpanData tool = span("execute_tool search");
    assertThat(consumingJob.getSpanId()).isNotEqualTo(discoveryJob.getSpanId());
    assertThat(tool.getParentSpanId()).isEqualTo(discoveryJob.getSpanId());
    assertThat(tool.getTraceId()).isEqualTo(discoveryJob.getTraceId());
    assertThat(tool.getStartEpochNanos()).isGreaterThanOrEqualTo(discoveryJob.getStartEpochNanos());
  }

  @Test
  void toolSpansOfOneTurnShareTheIssuingJobAsParent() {
    final var issuing =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));
    issuing.onTurnStarted(turnStarted(1, List.of()));
    chatRound(issuing, StopReason.TOOL_USE);
    final var state = issuing.onTurnCompleted(turnCompleted(1, true));
    issuing.onJobCompleted();
    final SpanData issuingJob = span("invoke_agent agent");
    exporter.reset();
    final Instant issuedAt = T0.plusSeconds(2);

    final var consuming =
        listener.onJobStarted(
            started(
                AgentJobKind.CONVERSATION, T0.plusSeconds(60), 1, withIssuedAt(state, issuedAt)));
    consuming.onTurnStarted(
        turnStarted(
            2,
            List.of(
                toolExecution("call-1", "search", T0.plusSeconds(20), false),
                toolExecution("call-2", "lookup", T0.plusSeconds(40), false),
                toolExecution("call-3", "approval", T0.plusSeconds(50), true))));
    chatRound(consuming, StopReason.STOP);
    consuming.onJobCompleted();

    assertThat(exporter.getFinishedSpanItems())
        .filteredOn(span -> span.getName().startsWith("execute_tool"))
        .hasSize(3)
        .allSatisfy(
            tool -> {
              assertThat(tool.getParentSpanId()).isEqualTo(issuingJob.getSpanId());
              assertThat(tool.getStartEpochNanos()).isEqualTo(nanos(issuedAt));
            });
  }

  @Test
  void aTurnThatIssuesToolCallsRecordsWhenItIsPersisted() {
    final var issuing =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));
    issuing.onTurnStarted(turnStarted(1, List.of()));
    chatRound(issuing, StopReason.TOOL_USE);
    clock.set(T0.plusSeconds(4));
    final var state = issuing.onTurnCompleted(turnCompleted(1, true));
    issuing.onJobCompleted();

    assertThat(issuedAtOf(state)).isEqualTo(T0.plusSeconds(4));
    assertThat(RunTraceState.fromMap(state))
        .hasValueSatisfying(parsed -> assertThat(parsed.issuedAt()).isEqualTo(issuedAtOf(state)));
  }

  @Test
  void aFinalAnswerAndAToolDiscoveryRecordNoIssueTime() {
    final var answering =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, null, Map.of()));
    answering.onTurnStarted(turnStarted(1, List.of()));
    chatRound(answering, StopReason.STOP);
    final var afterAnswer = answering.onTurnCompleted(turnCompleted(1, false));
    answering.onJobCompleted();

    final var discovery =
        listener.onJobStarted(
            started(AgentJobKind.TOOL_DISCOVERY, T0.plusSeconds(10), null, Map.of()));
    final var afterDiscovery =
        discovery.onToolDiscoveryDispatched(new ToolDiscoveryDispatched(List.of()));
    discovery.onJobCompleted();

    assertThat(afterAnswer).containsEntry("status", "CLOSED").doesNotContainKey("issuedAt");
    assertThat(afterDiscovery).doesNotContainKey("issuedAt");
  }

  @Test
  void aToolSpanNeverStartsAfterItsEnd() {
    final Instant completed = T0.plusSeconds(5);
    final var observer =
        listener.onJobStarted(
            started(
                AgentJobKind.CONVERSATION,
                T0.plusSeconds(60),
                1,
                withIssuedAt(openStateWithIssuer(1), completed.plusSeconds(3))));

    observer.onTurnStarted(
        turnStarted(2, List.of(toolExecution("call-1", "search", completed, false))));
    chatRound(observer, StopReason.STOP);
    observer.onJobCompleted();

    final SpanData tool = span("execute_tool search");
    assertThat(tool.getStartEpochNanos()).isEqualTo(nanos(completed));
    assertThat(tool.getEndEpochNanos()).isEqualTo(nanos(completed));
  }

  @Test
  void toolSpansStartAtTheConsumingJobWhenTheStateHasNoIssueTime() {
    final Instant jobStart = T0.plusSeconds(60);
    final Instant completedBefore = T0.plusSeconds(20);
    final Instant completedAfter = T0.plusSeconds(90);
    final var observer =
        listener.onJobStarted(
            started(AgentJobKind.CONVERSATION, jobStart, 1, openStateWithIssuer(1)));

    observer.onTurnStarted(
        turnStarted(
            2,
            List.of(
                toolExecution("call-1", "search", completedBefore, false),
                toolExecution("call-2", "lookup", completedAfter, false))));
    chatRound(observer, StopReason.STOP);
    observer.onJobCompleted();

    assertThat(span("execute_tool search").getStartEpochNanos()).isEqualTo(nanos(completedBefore));
    assertThat(span("execute_tool lookup").getStartEpochNanos()).isEqualTo(nanos(jobStart));
    assertThat(span("execute_tool lookup").getEndEpochNanos()).isEqualTo(nanos(completedAfter));
  }

  @Test
  void aStateOfAnEndedOrStaleRunLendsNoIssueTimeToANewRun() {
    final Instant issuedAt = T0.minusSeconds(3600);
    final Map<String, Object> closed =
        withIssuedAt(
            RunTraceState.of(
                    "4bf92f3577b34da6a3ce929d0e0e4736",
                    "00f067aa0ba902b7",
                    true,
                    RunTraceState.Status.CLOSED,
                    1,
                    "1111111111111111")
                .toMap(),
            issuedAt);
    final var observer = listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, 1, closed));

    observer.onTurnStarted(
        turnStarted(2, List.of(toolExecution("call-1", "search", T0.plusSeconds(5), false))));
    chatRound(observer, StopReason.TOOL_USE);
    final var state = observer.onTurnCompleted(turnCompleted(2, true));
    observer.onJobCompleted();

    assertThat(span("execute_tool search").getStartEpochNanos()).isEqualTo(nanos(T0));
    assertThat(issuedAtOf(state)).isAfter(issuedAt);
  }

  @Test
  void toolSpansFallBackToTheConsumingJobWhenTheStateHasNoIssuer() {
    final var observer =
        listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, 1, openState(1)));

    observer.onTurnStarted(
        turnStarted(2, List.of(toolExecution("call-1", "search", T0.plusSeconds(5), false))));
    chatRound(observer, StopReason.STOP);
    observer.onJobCompleted();

    assertThat(span("execute_tool search").getParentSpanId())
        .isEqualTo(span("invoke_agent agent").getSpanId());
  }

  @Test
  void toolSpansFallBackToTheConsumingJobWhenTheIssuerIsInvalid() {
    final Map<String, Object> state = new java.util.HashMap<>(openState(1));
    state.put("issuerSpanId", "0".repeat(16));
    final var observer = listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, 1, state));

    observer.onTurnStarted(
        turnStarted(2, List.of(toolExecution("call-1", "search", T0.plusSeconds(5), false))));
    chatRound(observer, StopReason.STOP);
    observer.onJobCompleted();

    assertThat(span("execute_tool search").getParentSpanId())
        .isEqualTo(span("invoke_agent agent").getSpanId());
  }

  @Test
  void aNewRunNeitherUsesNorCarriesTheIssuerOfTheEndedRun() {
    final Map<String, Object> closed =
        RunTraceState.of(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7",
                true,
                RunTraceState.Status.CLOSED,
                1,
                "1111111111111111")
            .toMap();
    final var observer = listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, 1, closed));

    observer.onTurnStarted(
        turnStarted(2, List.of(toolExecution("call-1", "search", T0.plusSeconds(5), false))));
    chatRound(observer, StopReason.TOOL_USE);
    final var state = observer.onTurnCompleted(turnCompleted(2, true));
    observer.onJobCompleted();

    final SpanData invokeAgent = span("invoke_agent agent");
    assertThat(invokeAgent.getTraceId()).isNotEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    assertThat(span("execute_tool search").getParentSpanId()).isEqualTo(invokeAgent.getSpanId());
    assertThat(state).containsEntry("issuerSpanId", invokeAgent.getSpanId());
  }

  @Test
  void aStaleStateDoesNotLendItsIssuerToANewRun() {
    final Map<String, Object> stale = new java.util.HashMap<>(openState(1));
    stale.put("issuerSpanId", "1111111111111111");
    final var observer = listener.onJobStarted(started(AgentJobKind.CONVERSATION, T0, 5, stale));

    observer.onTurnStarted(
        turnStarted(6, List.of(toolExecution("call-1", "search", T0.plusSeconds(5), false))));
    chatRound(observer, StopReason.STOP);
    observer.onJobCompleted();

    assertThat(span("execute_tool search").getParentSpanId())
        .isEqualTo(span("invoke_agent agent").getSpanId());
  }

  // --- helpers ---

  private Map<String, Object> openState(int iterationKey) {
    return RunTraceState.of(
            "4bf92f3577b34da6a3ce929d0e0e4736",
            "00f067aa0ba902b7",
            true,
            RunTraceState.Status.OPEN,
            iterationKey)
        .toMap();
  }

  private Map<String, Object> openStateWithIssuer(int iterationKey) {
    return RunTraceState.of(
            "4bf92f3577b34da6a3ce929d0e0e4736",
            "00f067aa0ba902b7",
            true,
            RunTraceState.Status.OPEN,
            iterationKey,
            "53995c3f42cd8ad8")
        .toMap();
  }

  private static Map<String, Object> withIssuedAt(Map<String, Object> state, Instant issuedAt) {
    final Map<String, Object> copy = new java.util.HashMap<>(state);
    copy.put("issuedAt", issuedAt.toEpochMilli());
    return copy;
  }

  private static Instant issuedAtOf(@Nullable Map<String, Object> state) {
    assertThat(state).containsKey("issuedAt");
    return Instant.ofEpochMilli((Long) state.get("issuedAt"));
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

  private static TurnStarted turnStarted(int iterationKey, List<ToolExecution> executions) {
    final List<Message> input =
        List.of(UserMessage.builder().content(List.of(TextContent.textContent("hi"))).build());
    return new TurnStarted(
        iterationKey,
        executions,
        input,
        null,
        CHAT_MODEL,
        List.of(
            ToolDefinition.builder()
                .name("search")
                .description("Search the web")
                .inputSchema(Map.of("type", "object"))
                .build()));
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
    return new AgentJobInfo(7, PROCESS_INSTANCE_KEY, 3, 4, "agent", "process", "<default>");
  }

  /** A job that starts now: the clock of the listener moves to the start time of the job. */
  private JobStarted started(
      AgentJobKind kind,
      Instant startedAt,
      @Nullable Integer lastIterationKey,
      Map<String, Object> previousState) {
    clock.set(startedAt);
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

  /** A clock that only moves when the test sets it. */
  private static final class MutableClock extends Clock {

    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void set(Instant now) {
      this.now = now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
