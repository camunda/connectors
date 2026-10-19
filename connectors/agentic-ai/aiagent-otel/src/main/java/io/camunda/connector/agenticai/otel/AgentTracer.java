/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Creates the spans of an agent run with explicit timestamps, remote parents, and links, which the
 * lifecycle events need because the spans of one run are written by different jobs, possibly days
 * apart.
 */
public interface AgentTracer {

  /**
   * Starts the root span of a run. Its trace id, span id, and sampling decision are assigned by the
   * SDK and are read from the returned span.
   *
   * @param link the anchor of the previous run of the same conversation, if any
   */
  Span startAnchor(String name, Instant start, Attributes attributes, @Nullable SpanContext link);

  /** Starts a span below the span of the given context. */
  Span startChild(String name, SpanKind kind, Instant start, Attributes attributes, Context parent);

  /** The context in which the given live span is the parent. */
  Context contextOf(Span span);

  /**
   * The context in which the anchor of a run that started in an earlier job is the parent. The
   * sampling decision of the anchor holds for every span of the run.
   */
  Context remoteParent(String traceId, String anchorSpanId, boolean sampled);

  /** Ends the span at the given time. */
  void end(Span span, Instant end);

  /**
   * Marks the span as failed with a low-cardinality error type. The description is attached only
   * when given, which callers do only with content capture.
   */
  void markError(Span span, String errorType, @Nullable String description);
}
