/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentTracerImplTest {

  private static final Instant T0 = Instant.parse("2026-10-19T09:00:00Z");

  private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
  private SdkTracerProvider tracerProvider;
  private AgentTracerImpl tracer;

  @BeforeEach
  void setUp() {
    tracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
    tracer =
        new AgentTracerImpl(OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build());
  }

  @AfterEach
  void tearDown() {
    tracerProvider.close();
  }

  @Test
  void childOfRebuiltRemoteParentJoinsTheAnchorTrace() {
    final Span anchor = tracer.startAnchor("agent run", T0, Attributes.empty(), null);
    final SpanContext anchorContext = anchor.getSpanContext();
    tracer.end(anchor, T0.plusSeconds(1));

    // a later job only knows the persisted ids
    final Context parent =
        tracer.remoteParent(anchorContext.getTraceId(), anchorContext.getSpanId(), true);
    final Span child =
        tracer.startChild(
            "invoke_agent", SpanKind.INTERNAL, T0.plusSeconds(3600), Attributes.empty(), parent);
    tracer.end(child, T0.plusSeconds(3601));

    final SpanData anchorData = span("agent run");
    final SpanData childData = span("invoke_agent");
    assertThat(anchorData.getParentSpanContext().isValid()).isFalse();
    assertThat(anchorData.getStartEpochNanos()).isEqualTo(nanos(T0));
    assertThat(childData.getTraceId()).isEqualTo(anchorData.getTraceId());
    assertThat(childData.getParentSpanId()).isEqualTo(anchorData.getSpanId());
  }

  @Test
  void toolSpanKeepsItsStartBeforeTheParentStart() {
    final Span anchor = tracer.startAnchor("agent run", T0, Attributes.empty(), null);
    final Span job =
        tracer.startChild(
            "invoke_agent",
            SpanKind.INTERNAL,
            T0.plusSeconds(100),
            Attributes.empty(),
            tracer.contextOf(anchor));
    final Span tool =
        tracer.startChild(
            "execute_tool search",
            SpanKind.INTERNAL,
            T0.plusSeconds(10),
            Attributes.empty(),
            tracer.contextOf(job));
    tracer.end(tool, T0.plusSeconds(90));
    tracer.end(job, T0.plusSeconds(101));
    tracer.end(anchor, T0.plusSeconds(101));

    final SpanData toolData = span("execute_tool search");
    assertThat(toolData.getStartEpochNanos()).isEqualTo(nanos(T0.plusSeconds(10)));
    assertThat(toolData.getEndEpochNanos()).isEqualTo(nanos(T0.plusSeconds(90)));
    assertThat(toolData.getParentSpanId()).isEqualTo(span("invoke_agent").getSpanId());
  }

  @Test
  void unsampledAnchorKeepsTheWholeRunOut() {
    final Context parent =
        tracer.remoteParent("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", false);

    final Span child =
        tracer.startChild("invoke_agent", SpanKind.INTERNAL, T0, Attributes.empty(), parent);
    tracer.end(child, T0.plusSeconds(1));

    assertThat(exporter.getFinishedSpanItems()).isEmpty();
  }

  @Test
  void anchorLinksToThePreviousAnchorAndNeverEndedAnchorIsNotExported() {
    final Span first = tracer.startAnchor("first", T0, Attributes.empty(), null);
    final Span second =
        tracer.startAnchor("second", T0.plusSeconds(5), Attributes.empty(), first.getSpanContext());
    tracer.end(second, T0.plusSeconds(6));

    assertThat(exporter.getFinishedSpanItems()).hasSize(1);
    final SpanData secondData = span("second");
    assertThat(secondData.getLinks()).hasSize(1);
    assertThat(secondData.getLinks().get(0).getSpanContext().getSpanId())
        .isEqualTo(first.getSpanContext().getSpanId());
  }

  @Test
  void marksErrorsWithoutDescriptionUnlessGiven() {
    final Span plain = tracer.startAnchor("plain", T0, Attributes.empty(), null);
    tracer.markError(plain, "job_superseded", null);
    tracer.end(plain, T0.plusSeconds(1));

    final SpanData data = span("plain");
    assertThat(data.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(data.getStatus().getDescription()).isEmpty();
    assertThat(data.getAttributes().get(GenAiAttributes.ERROR_TYPE)).isEqualTo("job_superseded");
  }

  private SpanData span(String name) {
    return exporter.getFinishedSpanItems().stream()
        .filter(span -> span.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static long nanos(Instant instant) {
    return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
  }
}
