/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The tracing state of one agent run, persisted by the lifecycle framework in the agent context
 * under the listener's namespace. It holds just enough to rebuild the run's remote parent in a
 * later job and to tell whether the run is still the current one.
 *
 * @param version the schema version of the persisted map
 * @param traceId the trace id of the run, 32 lowercase hex characters
 * @param anchorSpanId the span id of the run's anchor root, 16 lowercase hex characters
 * @param sampled the sampling decision made once on the anchor
 * @param status whether the run continues or its final answer was given
 * @param iterationKey the turn iteration key at the time the state was written
 * @param issuerSpanId the span id of the {@code invoke_agent} span of the job that wrote the state,
 *     16 lowercase hex characters, which is the job that issued the tool calls a later job
 *     consumes. Absent in a state that was written without it.
 */
public record RunTraceState(
    int version,
    String traceId,
    String anchorSpanId,
    boolean sampled,
    Status status,
    int iterationKey,
    @Nullable String issuerSpanId) {

  public static final int CURRENT_VERSION = 1;

  /** Whether the run is still open. */
  public enum Status {
    /** The last turn issued tool calls, or the job dispatched tool discovery. */
    OPEN,
    /** The last turn produced the final answer. */
    CLOSED
  }

  /** Creates a state of the current version without an issuer. */
  public static RunTraceState of(
      String traceId, String anchorSpanId, boolean sampled, Status status, int iterationKey) {
    return of(traceId, anchorSpanId, sampled, status, iterationKey, null);
  }

  /** Creates a state of the current version. */
  public static RunTraceState of(
      String traceId,
      String anchorSpanId,
      boolean sampled,
      Status status,
      int iterationKey,
      @Nullable String issuerSpanId) {
    return new RunTraceState(
        CURRENT_VERSION, traceId, anchorSpanId, sampled, status, iterationKey, issuerSpanId);
  }

  /** The JSON-friendly form that is stored in the agent context. */
  public Map<String, Object> toMap() {
    final Map<String, Object> map = new LinkedHashMap<>();
    map.put("version", version);
    map.put("traceId", traceId);
    map.put("anchorSpanId", anchorSpanId);
    map.put("sampled", sampled);
    map.put("status", status.name());
    map.put("iterationKey", iterationKey);
    if (issuerSpanId != null) {
      map.put("issuerSpanId", issuerSpanId);
    }
    return map;
  }

  /**
   * Reads a stored state. Returns empty for anything missing, malformed, or of another version: a
   * trace or span id that is not valid, a number that is not an integer within the int range, or an
   * unknown status. Entries the current layout does not define, such as the process instance key of
   * earlier states, are ignored. A missing or invalid issuer span id leaves the issuer absent and
   * does not invalidate the state.
   */
  public static Optional<RunTraceState> fromMap(Map<String, Object> map) {
    try {
      if (!Integer.valueOf(CURRENT_VERSION).equals(toInt(map.get("version")))) {
        return Optional.empty();
      }
      final Integer iterationKey = toInt(map.get("iterationKey"));
      if (!(map.get("traceId") instanceof String traceId)
          || !TraceId.isValid(traceId)
          || !(map.get("anchorSpanId") instanceof String anchorSpanId)
          || !SpanId.isValid(anchorSpanId)
          || !(map.get("sampled") instanceof Boolean sampled)
          || !(map.get("status") instanceof String statusName)
          || iterationKey == null) {
        return Optional.empty();
      }
      final String issuerSpanId =
          map.get("issuerSpanId") instanceof String id && SpanId.isValid(id) ? id : null;
      return Optional.of(
          of(
              traceId,
              anchorSpanId,
              sampled,
              Status.valueOf(statusName),
              iterationKey,
              issuerSpanId));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  /** The value as an int, or null when it is not a number, not integral, or out of range. */
  private static @Nullable Integer toInt(@Nullable Object value) {
    if (!(value instanceof Number number)) {
      return null;
    }
    if (number instanceof Integer || number instanceof Short || number instanceof Byte) {
      return number.intValue();
    }
    final double asDouble = number.doubleValue();
    if (number instanceof Long) {
      final long asLong = number.longValue();
      return asLong >= Integer.MIN_VALUE && asLong <= Integer.MAX_VALUE ? (int) asLong : null;
    }
    if (Double.isNaN(asDouble)
        || Double.isInfinite(asDouble)
        || asDouble != Math.rint(asDouble)
        || asDouble < Integer.MIN_VALUE
        || asDouble > Integer.MAX_VALUE) {
      return null;
    }
    return (int) asDouble;
  }
}
