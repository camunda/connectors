/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.otel.RunTraceState.Status;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunTraceStateTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String SPAN_ID = "00f067aa0ba902b7";

  @Test
  void readsAValidStateWithAnyIntegralNumberType() {
    assertThat(RunTraceState.fromMap(stored("iterationKey", 3L)))
        .contains(RunTraceState.of(TRACE_ID, SPAN_ID, true, Status.OPEN, 3));
    assertThat(RunTraceState.fromMap(stored("iterationKey", 3.0d)))
        .contains(RunTraceState.of(TRACE_ID, SPAN_ID, true, Status.OPEN, 3));
    assertThat(RunTraceState.fromMap(stored("version", 1L))).isPresent();
  }

  @Test
  void rejectsInvalidTraceAndSpanIds() {
    assertThat(RunTraceState.fromMap(stored("traceId", "0".repeat(32)))).isEmpty();
    assertThat(RunTraceState.fromMap(stored("traceId", TRACE_ID.substring(1)))).isEmpty();
    assertThat(RunTraceState.fromMap(stored("traceId", TRACE_ID.toUpperCase()))).isEmpty();
    assertThat(RunTraceState.fromMap(stored("traceId", 42))).isEmpty();
    assertThat(RunTraceState.fromMap(stored("anchorSpanId", "0".repeat(16)))).isEmpty();
    assertThat(RunTraceState.fromMap(stored("anchorSpanId", SPAN_ID + "0"))).isEmpty();
  }

  @Test
  void rejectsIterationKeysThatAreNotIntegersWithinTheIntRange() {
    assertThat(RunTraceState.fromMap(stored("iterationKey", 1.5d))).isEmpty();
    assertThat(RunTraceState.fromMap(stored("iterationKey", new BigDecimal("2.25")))).isEmpty();
    assertThat(RunTraceState.fromMap(stored("iterationKey", 3_000_000_000L))).isEmpty();
    assertThat(RunTraceState.fromMap(stored("iterationKey", 1e12d))).isEmpty();
    assertThat(RunTraceState.fromMap(stored("iterationKey", Double.NaN))).isEmpty();
    assertThat(RunTraceState.fromMap(stored("iterationKey", "3"))).isEmpty();
  }

  @Test
  void rejectsAnUnknownStatusAndAFractionalVersionWithoutThrowing() {
    assertThat(RunTraceState.fromMap(stored("status", "PAUSED"))).isEmpty();
    assertThat(RunTraceState.fromMap(stored("version", 1.5d))).isEmpty();
    assertThat(RunTraceState.fromMap(Map.of())).isEmpty();
  }

  private static Map<String, Object> stored(String key, Object value) {
    final Map<String, Object> map =
        new HashMap<>(RunTraceState.of(TRACE_ID, SPAN_ID, true, Status.OPEN, 1).toMap());
    map.put(key, value);
    return map;
  }
}
