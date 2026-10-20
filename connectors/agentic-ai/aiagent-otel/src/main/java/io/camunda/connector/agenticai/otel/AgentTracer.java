/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Creates the spans of an agent run on the runtime's {@link OpenTelemetry}, with its own
 * instrumentation scope. Spans get explicit timestamps and remote parents, which the lifecycle
 * events need because the spans of one run are written by different jobs, possibly days apart.
 */
public class AgentTracer {

  public static final String INSTRUMENTATION_SCOPE = "io.camunda.connector.agenticai";

  private final Tracer tracer;

  public AgentTracer(OpenTelemetry openTelemetry) {
    this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);
  }

  /**
   * Starts the root span of a run, which the caller ends at its start time to export it as a
   * marker. Its trace id, span id, and sampling decision are assigned by the SDK and are read from
   * the returned span.
   */
  public Span startAnchor(String name, Instant start, Attributes attributes) {
    return tracer
        .spanBuilder(name)
        .setNoParent()
        .setSpanKind(SpanKind.INTERNAL)
        .setStartTimestamp(start)
        .setAllAttributes(attributes)
        .startSpan();
  }

  /** Starts a span below the span of the given context. */
  public Span startChild(
      String name, SpanKind kind, Instant start, Attributes attributes, Context parent) {
    return tracer
        .spanBuilder(name)
        .setParent(parent)
        .setSpanKind(kind)
        .setStartTimestamp(start)
        .setAllAttributes(attributes)
        .startSpan();
  }

  /** The context in which the given live span is the parent. */
  public Context contextOf(Span span) {
    return Context.root().with(span);
  }

  /**
   * The context in which a span that an earlier job exported, such as the anchor of the run, is the
   * parent. The sampling decision of the anchor holds for every span of the run.
   */
  public Context remoteParent(String traceId, String spanId, boolean sampled) {
    final SpanContext remote =
        SpanContext.createFromRemoteParent(
            traceId,
            spanId,
            sampled ? TraceFlags.getSampled() : TraceFlags.getDefault(),
            TraceState.getDefault());
    return Context.root().with(Span.wrap(remote));
  }

  /** Ends the span at the given time. */
  public void end(Span span, Instant end) {
    span.end(end);
  }

  /**
   * Marks the span as failed with a low-cardinality error type. The description is attached only
   * when given, which callers do only with content capture.
   */
  public void markError(Span span, String errorType, @Nullable String description) {
    setErrorType(span, errorType);
    if (description != null) {
      span.setStatus(StatusCode.ERROR, description);
    } else {
      span.setStatus(StatusCode.ERROR);
    }
  }

  /** Records the error type without marking the span as failed, its status stays unset. */
  public void setErrorType(Span span, String errorType) {
    span.setAttribute(GenAiAttributes.ERROR_TYPE, errorType);
  }
}
