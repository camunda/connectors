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
import io.opentelemetry.api.trace.SpanBuilder;
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
 * {@link AgentTracer} over the runtime's {@link OpenTelemetry}, with its own instrumentation scope.
 */
public class AgentTracerImpl implements AgentTracer {

  public static final String INSTRUMENTATION_SCOPE = "io.camunda.connector.agenticai";

  private final Tracer tracer;

  public AgentTracerImpl(OpenTelemetry openTelemetry) {
    this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);
  }

  @Override
  public Span startAnchor(
      String name, Instant start, Attributes attributes, @Nullable SpanContext link) {
    final SpanBuilder builder =
        tracer
            .spanBuilder(name)
            .setNoParent()
            .setSpanKind(SpanKind.INTERNAL)
            .setStartTimestamp(start)
            .setAllAttributes(attributes);
    if (link != null && link.isValid()) {
      builder.addLink(link);
    }
    return builder.startSpan();
  }

  @Override
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

  @Override
  public Context contextOf(Span span) {
    return Context.root().with(span);
  }

  @Override
  public Context remoteParent(String traceId, String anchorSpanId, boolean sampled) {
    final SpanContext remote =
        SpanContext.createFromRemoteParent(
            traceId,
            anchorSpanId,
            sampled ? TraceFlags.getSampled() : TraceFlags.getDefault(),
            TraceState.getDefault());
    return Context.root().with(Span.wrap(remote));
  }

  @Override
  public void end(Span span, Instant end) {
    span.end(end);
  }

  @Override
  public void markError(Span span, String errorType, @Nullable String description) {
    span.setAttribute(GenAiAttributes.ERROR_TYPE, errorType);
    if (description != null) {
      span.setStatus(StatusCode.ERROR, description);
    } else {
      span.setStatus(StatusCode.ERROR);
    }
  }
}
