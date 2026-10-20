/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.otel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

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
 */
public record RunTraceState(
    int version,
    String traceId,
    String anchorSpanId,
    boolean sampled,
    Status status,
    int iterationKey) {

  public static final int CURRENT_VERSION = 1;

  private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");
  private static final Pattern SPAN_ID = Pattern.compile("[0-9a-f]{16}");

  /** Whether the run is still open. */
  public enum Status {
    /** The last turn issued tool calls, or the job dispatched tool discovery. */
    OPEN,
    /** The last turn produced the final answer. */
    CLOSED
  }

  /** Creates a state of the current version. */
  public static RunTraceState of(
      String traceId, String anchorSpanId, boolean sampled, Status status, int iterationKey) {
    return new RunTraceState(CURRENT_VERSION, traceId, anchorSpanId, sampled, status, iterationKey);
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
    return map;
  }

  /** Reads a stored state. Returns empty for anything missing, malformed, or of another version. */
  public static Optional<RunTraceState> fromMap(Map<String, Object> map) {
    try {
      if (!(map.get("version") instanceof Number version)
          || version.intValue() != CURRENT_VERSION) {
        return Optional.empty();
      }
      if (!(map.get("traceId") instanceof String traceId)
          || !TRACE_ID.matcher(traceId).matches()
          || !(map.get("anchorSpanId") instanceof String anchorSpanId)
          || !SPAN_ID.matcher(anchorSpanId).matches()
          || !(map.get("sampled") instanceof Boolean sampled)
          || !(map.get("status") instanceof String statusName)
          || !(map.get("iterationKey") instanceof Number iterationKey)) {
        return Optional.empty();
      }
      return Optional.of(
          of(traceId, anchorSpanId, sampled, Status.valueOf(statusName), iterationKey.intValue()));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }
}
