/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.otel.RunTraceState.Status;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunTraceStateTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String SPAN_ID = "00f067aa0ba902b7";
  private static final String ISSUER_SPAN_ID = "53995c3f42cd8ad8";

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

  @Test
  void roundTripsTheIssuerSpanId() {
    final RunTraceState state =
        RunTraceState.of(TRACE_ID, SPAN_ID, true, Status.OPEN, 2, ISSUER_SPAN_ID);

    assertThat(state.toMap()).containsEntry("issuerSpanId", ISSUER_SPAN_ID);
    assertThat(RunTraceState.fromMap(state.toMap())).contains(state);
  }

  @Test
  void readsAStateWrittenWithoutAnIssuerSpanId() {
    final Map<String, Object> earlier = stored("status", "OPEN");
    assertThat(earlier).doesNotContainKey("issuerSpanId");

    assertThat(RunTraceState.fromMap(earlier))
        .hasValueSatisfying(state -> assertThat(state.issuerSpanId()).isNull());
  }

  @Test
  void dropsAnInvalidIssuerSpanIdButKeepsTheState() {
    for (final Object invalid :
        new Object[] {"0".repeat(16), ISSUER_SPAN_ID + "0", ISSUER_SPAN_ID.toUpperCase(), 42}) {
      assertThat(RunTraceState.fromMap(stored("issuerSpanId", invalid)))
          .hasValueSatisfying(state -> assertThat(state.issuerSpanId()).isNull());
    }
  }

  @Test
  void roundTripsTheIssueTimeAsEpochMilliseconds() {
    final Instant issuedAt = Instant.parse("2026-10-19T09:00:06.123Z");
    final RunTraceState state =
        RunTraceState.of(TRACE_ID, SPAN_ID, true, Status.OPEN, 2, ISSUER_SPAN_ID, issuedAt);

    assertThat(state.toMap()).containsEntry("issuedAt", issuedAt.toEpochMilli());
    assertThat(RunTraceState.fromMap(state.toMap())).contains(state);
  }

  @Test
  void readsTheIssueTimeFromAnyIntegralNumberType() {
    final long millis = Instant.parse("2026-10-19T09:00:06.123Z").toEpochMilli();
    for (final Object stored : new Object[] {millis, (double) millis, new BigDecimal(millis)}) {
      assertThat(RunTraceState.fromMap(stored("issuedAt", stored)))
          .hasValueSatisfying(
              state -> assertThat(state.issuedAt()).isEqualTo(Instant.ofEpochMilli(millis)));
    }
  }

  @Test
  void readsAStateWrittenWithoutAnIssueTime() {
    final Map<String, Object> earlier = stored("status", "OPEN");
    assertThat(earlier).doesNotContainKey("issuedAt");

    assertThat(RunTraceState.fromMap(earlier))
        .hasValueSatisfying(state -> assertThat(state.issuedAt()).isNull());
  }

  @Test
  void dropsAnInvalidIssueTimeButKeepsTheState() {
    for (final Object invalid :
        new Object[] {"1760864406123", -1L, 1.5d, Double.NaN, new BigDecimal("1.5"), true}) {
      assertThat(RunTraceState.fromMap(stored("issuedAt", invalid)))
          .hasValueSatisfying(state -> assertThat(state.issuedAt()).isNull());
    }
  }

  @Test
  void aFullStateStaysWellUnderTheListenerStateLimit() throws Exception {
    final Map<String, Object> map =
        RunTraceState.of(
                TRACE_ID,
                SPAN_ID,
                true,
                Status.CLOSED,
                Integer.MAX_VALUE,
                ISSUER_SPAN_ID,
                Instant.parse("2026-10-19T09:00:06.123Z"))
            .toMap();

    final int bytes =
        new ObjectMapper().writeValueAsString(map).getBytes(StandardCharsets.UTF_8).length;

    assertThat(bytes).isLessThan(256);
  }

  private static Map<String, Object> stored(String key, Object value) {
    final Map<String, Object> map =
        new HashMap<>(RunTraceState.of(TRACE_ID, SPAN_ID, true, Status.OPEN, 1).toMap());
    map.put(key, value);
    return map;
  }
}
