/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

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
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.otel.RunTraceState.Status;
import io.camunda.connector.agenticai.otel.RunTraceStateResolver.Resolution;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import io.camunda.connector.api.outbound.JobCompletionFailure.BpmnErrorThrown;
import io.camunda.connector.api.outbound.JobCompletionFailure.CommandFailure.CommandFailed;
import io.camunda.connector.api.outbound.JobCompletionFailure.CommandFailure.CommandIgnored;
import io.camunda.connector.api.outbound.JobCompletionFailure.ExecutionFailed;
import io.camunda.connector.api.outbound.JobCompletionFailure.JobErrorRaised;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the spans of one job. The spans start when the job reports the start of a turn or the
 * dispatch of a tool discovery, which only a job that holds the lease reports, so a job that is
 * superseded by the write that fences it, or that completes without either, emits nothing. A job
 * that fails before either start is a failed job and exports its failed spans, unless it failed
 * because it was superseded. The {@code invoke_agent} span ends exactly once, at the first of job
 * failed, job completed, and job completion failed. The anchor of a run this job starts is a
 * zero-duration marker at the job start and ends as soon as it starts. No callback lets a failure
 * escape.
 */
class AgentTracingJobObserver implements AgentJobObserver {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentTracingJobObserver.class);

  /**
   * The error code of a write rejected because a newer activation of the job took over. It mirrors
   * the code of the AI Agent connector, which this module cannot depend on.
   */
  private static final String AGENT_INSTANCE_SUPERSEDED = "AGENT_INSTANCE_SUPERSEDED";

  private final AgentTracer tracer;
  private final JobStarted job;
  private final Resolution resolution;
  private final boolean captureContent;
  private final Clock clock;
  private final Attributes identity;

  private boolean opened;
  private boolean ended;

  private @Nullable Span invokeAgent;
  private @Nullable Context invokeAgentContext;

  /**
   * The parent of the tool spans: the {@code invoke_agent} span of the job that issued the tool
   * calls when the run state names it, otherwise the {@code invoke_agent} span of this job.
   */
  private @Nullable Context toolSpanParent;

  private @Nullable Span chat;

  /** The run the spans of this job belong to. */
  private @Nullable RunTraceState run;

  private List<ToolExecution> toolExecutions = List.of();
  private Map<String, ToolDefinition> toolDefinitions = Map.of();

  AgentTracingJobObserver(
      AgentTracer tracer,
      JobStarted job,
      Resolution resolution,
      boolean captureContent,
      Clock clock) {
    this.tracer = tracer;
    this.job = job;
    this.resolution = resolution;
    this.captureContent = captureContent;
    this.clock = clock;
    this.identity =
        GenAiAttributeMapper.identityAttributes(
            job.job(), job.agentInstanceKey(), resolution.sessionId());
  }

  @Override
  public synchronized @Nullable Map<String, Object> onToolDiscoveryDispatched(
      ToolDiscoveryDispatched event) {
    return guarded(
        "tool discovery dispatched",
        () -> {
          open(null);
          // a discovery dispatch persists no turn, so the iteration key stays what the context
          // already holds; this also covers a re-discovery in the middle of a run
          final Integer lastIterationKey = job.lastIterationKey();
          return stateToContribute(
              Status.OPEN, lastIterationKey != null ? lastIterationKey : 0, null);
        });
  }

  @Override
  public synchronized void onTurnStarted(TurnStarted event) {
    guarded(
        "turn started",
        () -> {
          this.toolExecutions = List.copyOf(event.toolExecutions());
          if (captureContent) {
            this.toolDefinitions = toolDefinitionsByName(event.toolDefinitions());
          }
          open(event);
          return null;
        });
  }

  @Override
  public synchronized void onModelRoundStarted(ModelRoundStarted event) {
    guarded(
        "model round started",
        () -> {
          final Context parent = invokeAgentContext;
          if (parent == null || chat != null) {
            return null;
          }
          final Attributes attributes =
              identity.toBuilder()
                  .putAll(
                      GenAiAttributeMapper.chatRequestAttributes(
                          event.chatModel(),
                          event.messages(),
                          event.toolDefinitions(),
                          captureContent))
                  .build();
          chat =
              tracer.startChild(
                  "chat " + event.chatModel().model(),
                  SpanKind.CLIENT,
                  clock.instant(),
                  attributes,
                  parent);
          return null;
        });
  }

  @Override
  public synchronized void onModelRoundCompleted(ModelRoundCompleted event) {
    guarded(
        "model round completed",
        () -> {
          final Span span = chat;
          if (span != null) {
            chat = null;
            endSpan(
                span,
                clock.instant(),
                () ->
                    span.setAllAttributes(
                        GenAiAttributeMapper.chatResponseAttributes(
                            event.assistantMessage(),
                            event.metrics().tokenUsage(),
                            captureContent)));
          }
          return null;
        });
  }

  @Override
  public synchronized void onModelRoundFailed(ModelRoundFailed event) {
    guarded(
        "model round failed",
        () -> {
          final Span span = chat;
          if (span != null) {
            chat = null;
            endSpan(
                span,
                clock.instant(),
                () -> {
                  span.setAttribute(
                      GenAiAttributes.RESPONSE_FINISH_REASONS,
                      List.of(GenAiAttributes.FINISH_REASON_ERROR));
                  tracer.markError(
                      span,
                      GenAiAttributeMapper.errorTypeOf(event.cause(), event.errorCode()),
                      description(event.cause()));
                });
          }
          return null;
        });
  }

  @Override
  public synchronized @Nullable Map<String, Object> onTurnCompleted(TurnCompleted event) {
    return guarded(
        "turn completed",
        () -> {
          final Span agentSpan = invokeAgent;
          final AssistantMessage assistantMessage = event.assistantMessage();
          if (captureContent && agentSpan != null && assistantMessage != null) {
            agentSpan.setAttribute(
                GenAiAttributes.OUTPUT_MESSAGES,
                GenAiAttributeMapper.outputMessagesJson(assistantMessage));
          }
          return stateToContribute(
              event.finalAnswer() ? Status.CLOSED : Status.OPEN,
              event.iterationKey(),
              event.finalAnswer() ? null : clock.instant());
        });
  }

  @Override
  public synchronized void onJobFailed(JobFailed event) {
    guarded(
        "job failed",
        () -> {
          if (AGENT_INSTANCE_SUPERSEDED.equals(event.errorCode())) {
            endAll(
                GenAiAttributes.JOB_OUTCOME_SUPERSEDED,
                GenAiAttributes.ERROR_TYPE_JOB_SUPERSEDED,
                null,
                false);
            return null;
          }
          // a failure before the start of a turn is still a failed job worth seeing
          open(null);
          endAll(
              GenAiAttributes.JOB_OUTCOME_FAILED,
              GenAiAttributeMapper.errorTypeOf(event.cause(), event.errorCode()),
              description(event.cause()),
              false);
          return null;
        });
  }

  @Override
  public synchronized void onJobCompleted() {
    guarded(
        "job completed",
        () -> {
          endAll(GenAiAttributes.JOB_OUTCOME_COMPLETED, null, null, true);
          return null;
        });
  }

  @Override
  public synchronized void onJobCompletionFailed(JobCompletionFailure failure) {
    guarded(
        "job completion failed",
        () -> {
          switch (failure) {
            case CommandIgnored ignored ->
                endAll(
                    GenAiAttributes.JOB_OUTCOME_SUPERSEDED,
                    GenAiAttributes.ERROR_TYPE_JOB_SUPERSEDED,
                    null,
                    false);
            case BpmnErrorThrown bpmnError ->
                endAll(
                    GenAiAttributes.JOB_OUTCOME_BPMN_ERROR,
                    bpmnError.errorCode(),
                    captureContent ? bpmnError.errorMessage() : null,
                    false);
            case ExecutionFailed executionFailed ->
                endAll(
                    GenAiAttributes.JOB_OUTCOME_FAILED,
                    errorTypeOf(executionFailed.cause()),
                    description(executionFailed.cause()),
                    false);
            case CommandFailed commandFailed ->
                endAll(
                    GenAiAttributes.JOB_OUTCOME_FAILED,
                    errorTypeOf(commandFailed.cause()),
                    description(commandFailed.cause()),
                    false);
            case JobErrorRaised errorRaised ->
                endAll(
                    GenAiAttributes.JOB_OUTCOME_FAILED,
                    "job_error_raised",
                    captureContent ? errorRaised.errorMessage() : null,
                    false);
          }
          return null;
        });
  }

  /**
   * Starts the spans of the job once.
   *
   * @param startedTurn the turn this job starts, null for a job that starts none
   */
  private void open(@Nullable TurnStarted startedTurn) {
    if (opened || ended) {
      return;
    }
    opened = true;

    final RunTraceState previous = resolution.previous();
    final Context parent;
    if (resolution.startsRun()) {
      final String runInput = runInput(startedTurn);
      final Span anchorSpan =
          tracer.startAnchor(
              GenAiAttributeMapper.anchorName(job.job().elementId(), runInput),
              job.startedAt(),
              anchorAttributes(runInput));
      parent = tracer.contextOf(anchorSpan);
      final SpanContext anchorContext = anchorSpan.getSpanContext();
      run =
          anchorContext.isValid()
              ? RunTraceState.of(
                  anchorContext.getTraceId(),
                  anchorContext.getSpanId(),
                  anchorContext.isSampled(),
                  Status.OPEN,
                  0)
              : null;
      // the anchor is a marker: it carries only what is known at the start of the run, the outcome
      // of the job is on the invoke_agent span
      endSpan(anchorSpan, job.startedAt(), () -> {});
    } else if (previous != null) {
      parent = tracer.remoteParent(previous.traceId(), previous.anchorSpanId(), previous.sampled());
      run = previous;
    } else {
      // a continued run always has a previous state; defensive only
      return;
    }

    final Span span =
        tracer.startChild(
            "invoke_agent " + job.job().elementId(),
            SpanKind.INTERNAL,
            job.startedAt(),
            invokeAgentAttributes(),
            parent);
    invokeAgent = span;
    final Context agentContext = tracer.contextOf(span);
    invokeAgentContext = agentContext;
    toolSpanParent = issuerContext(previous).orElse(agentContext);
    if (captureContent && startedTurn != null) {
      final String input = GenAiAttributeMapper.inputMessagesJson(startedTurn.inputMessages());
      if (input != null) {
        span.setAttribute(GenAiAttributes.INPUT_MESSAGES, input);
      }
    }
  }

  /**
   * The context of the {@code invoke_agent} span of the job that issued the tool calls this job
   * consumes. Only a job that continues the run has one: the state of a run that ended or went
   * stale names a span of another trace.
   */
  private Optional<Context> issuerContext(@Nullable RunTraceState previous) {
    if (!resolution.continuesRun() || previous == null) {
      return Optional.empty();
    }
    final String issuerSpanId = previous.issuerSpanId();
    if (issuerSpanId == null || !SpanId.isValid(issuerSpanId)) {
      return Optional.empty();
    }
    return Optional.of(tracer.remoteParent(previous.traceId(), issuerSpanId, previous.sampled()));
  }

  /** The text that triggered the run, known only with content capture and a turn in this job. */
  private @Nullable String runInput(@Nullable TurnStarted startedTurn) {
    return captureContent && startedTurn != null
        ? GenAiAttributeMapper.runInput(startedTurn.inputMessages())
        : null;
  }

  private Attributes anchorAttributes(@Nullable String runInput) {
    final AttributesBuilder builder = identity.toBuilder();
    builder.put(GenAiAttributes.AGENT_NAME, job.job().elementId());
    builder.put(
        GenAiAttributes.RUN_TRIGGER,
        resolution.previous() == null
            ? GenAiAttributes.RUN_TRIGGER_INITIAL
            : GenAiAttributes.RUN_TRIGGER_FOLLOW_UP);
    if (runInput != null) {
      builder.put(GenAiAttributes.INPUT_VALUE, runInput);
      builder.put(
          GenAiAttributes.OPENINFERENCE_SPAN_KIND, GenAiAttributes.OPENINFERENCE_SPAN_KIND_AGENT);
    }
    return builder.build();
  }

  private Attributes invokeAgentAttributes() {
    final AttributesBuilder builder = identity.toBuilder();
    builder.put(GenAiAttributes.OPERATION_NAME, GenAiAttributes.OPERATION_INVOKE_AGENT);
    builder.put(GenAiAttributes.AGENT_NAME, job.job().elementId());
    builder.put(GenAiAttributes.JOB_KEY, Long.toString(job.job().jobKey()));
    builder.put(GenAiAttributes.JOB_KIND, job.kind().name().toLowerCase(Locale.ROOT));
    return builder.build();
  }

  /**
   * The state of the run after this job, or null when the run has no valid trace.
   *
   * @param issuedAt when the turn of this job issued tool calls, null for a state that issues none
   */
  private @Nullable Map<String, Object> stateToContribute(
      Status status, int iterationKey, @Nullable Instant issuedAt) {
    final RunTraceState current = run;
    if (current == null) {
      return null;
    }
    final Span agentSpan = invokeAgent;
    final String issuerSpanId =
        agentSpan != null && agentSpan.getSpanContext().isValid()
            ? agentSpan.getSpanContext().getSpanId()
            : null;
    return RunTraceState.of(
            current.traceId(),
            current.anchorSpanId(),
            current.sampled(),
            status,
            iterationKey,
            issuerSpanId,
            issuedAt)
        .toMap();
  }

  /**
   * Ends the spans of the job. The first call wins. A job that started no spans exports nothing.
   */
  private void endAll(
      String outcome, @Nullable String errorType, @Nullable String description, boolean tools) {
    if (ended) {
      return;
    }
    ended = true;
    if (!opened) {
      return;
    }
    final boolean superseded = GenAiAttributes.JOB_OUTCOME_SUPERSEDED.equals(outcome);
    final Instant now = clock.instant();

    final Span openChat = chat;
    if (openChat != null) {
      chat = null;
      endSpan(
          openChat,
          now,
          () -> {
            openChat.setAttribute(
                GenAiAttributes.RESPONSE_FINISH_REASONS,
                List.of(GenAiAttributes.FINISH_REASON_ERROR));
            tracer.markError(
                openChat,
                errorType != null ? errorType : GenAiAttributes.ERROR_TYPE_JOB_ABANDONED,
                description);
          });
    }
    final Span agentSpan = invokeAgent;
    final Context toolParent = toolSpanParent;
    if (tools && toolParent != null) {
      try {
        emitToolSpans(toolParent, now);
      } catch (Throwable t) {
        LOGGER.debug("Agent tracing could not emit the tool spans", t);
      }
    }
    if (agentSpan != null) {
      endSpan(
          agentSpan,
          now,
          () -> {
            agentSpan.setAttribute(GenAiAttributes.JOB_OUTCOME, outcome);
            markJobError(agentSpan, superseded, errorType, description);
          });
    }
  }

  /**
   * A superseded job is not a failure of the agent: it carries the error type, and its status stays
   * unset.
   */
  private void markJobError(
      Span span, boolean superseded, @Nullable String errorType, @Nullable String description) {
    if (errorType == null) {
      return;
    }
    if (superseded) {
      tracer.setErrorType(span, errorType);
    } else {
      tracer.markError(span, errorType, description);
    }
  }

  /** Ends the span even when decorating it fails, and never lets a failure escape. */
  private void endSpan(Span span, Instant end, Runnable decorate) {
    try {
      decorate.run();
    } catch (Throwable t) {
      LOGGER.debug("Agent tracing could not complete a span", t);
    } finally {
      try {
        tracer.end(span, end);
      } catch (Throwable t) {
        LOGGER.debug("Agent tracing could not end a span", t);
      }
    }
  }

  /**
   * The time the tool calls consumed by this job were dispatched: the time the issuing job recorded
   * its turn, or the start of this job when the run state does not hold one.
   */
  private Instant toolCallsIssuedAt() {
    final RunTraceState previous = resolution.previous();
    final Instant recorded =
        resolution.continuesRun() && previous != null ? previous.issuedAt() : null;
    return recorded != null ? recorded : job.startedAt();
  }

  /**
   * One span per tool result consumed by the turn, from the dispatch of the tool call to its
   * completion, below the {@code invoke_agent} span of the job that issued the call. The start
   * never lies after the end, because the clock of the worker that recorded the dispatch can differ
   * from the one of the engine that recorded the completion.
   */
  private void emitToolSpans(Context parent, Instant now) {
    final Instant issuedAt = toolCallsIssuedAt();
    for (final ToolExecution execution : toolExecutions) {
      try {
        final var result = execution.result();
        final Instant end = result.completedAt() != null ? result.completedAt().toInstant() : now;
        final Instant start = issuedAt.isBefore(end) ? issuedAt : end;

        final AttributesBuilder builder = identity.toBuilder();
        builder.putAll(GenAiAttributeMapper.toolIdentityAttributes(execution.call(), result));
        builder.put(GenAiAttributes.AGENT_NAME, job.job().elementId());
        builder.put(
            GenAiAttributes.TOOL_OUTCOME,
            execution.cancelled()
                ? GenAiAttributes.TOOL_OUTCOME_CANCELLED
                : GenAiAttributes.TOOL_OUTCOME_COMPLETED);
        builder.putAll(
            GenAiAttributeMapper.toolContentAttributes(execution.call(), result, captureContent));

        final String name = execution.call() != null ? execution.call().name() : result.name();
        final ToolDefinition definition = name != null ? toolDefinitions.get(name) : null;
        if (captureContent && definition != null && definition.description() != null) {
          builder.put(GenAiAttributes.TOOL_DESCRIPTION, definition.description());
        }
        final Span span =
            tracer.startChild(
                name != null ? "execute_tool " + name : "execute_tool",
                SpanKind.INTERNAL,
                start,
                builder.build(),
                parent);
        tracer.end(span, end);
      } catch (Throwable t) {
        LOGGER.debug("Agent tracing could not emit a tool span", t);
      }
    }
  }

  private static Map<String, ToolDefinition> toolDefinitionsByName(
      List<ToolDefinition> definitions) {
    final Map<String, ToolDefinition> byName = new HashMap<>();
    for (final ToolDefinition definition : definitions) {
      byName.putIfAbsent(definition.name(), definition);
    }
    return byName;
  }

  /** The error type of a failure that has no error code of its own. */
  private static String errorTypeOf(Throwable cause) {
    return GenAiAttributeMapper.errorTypeOf(
        cause, cause instanceof ConnectorException ce ? ce.getErrorCode() : null);
  }

  /** The exception message, only with content capture: it can contain prompt or tool content. */
  private @Nullable String description(Throwable cause) {
    return captureContent ? cause.getMessage() : null;
  }

  /** Runs a callback body; telemetry never fails the job. */
  private <T> @Nullable T guarded(String callback, Supplier<@Nullable T> body) {
    try {
      return body.get();
    } catch (Throwable t) {
      LOGGER.debug("Agent tracing failed on {}", callback, t);
      return null;
    }
  }
}
