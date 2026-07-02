/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Resolves a concrete {@code completedAt} for every {@link ToolCallResult} at the earliest
 * ingestion point, per ADR 008: engine timestamp (A) primary, worker-observed first-seen time (B)
 * fallback, {@code now()} last resort. After resolution every result carries a non-null {@code
 * completedAt}.
 *
 * <p>The first-seen map (B) is persisted in {@link AgentContext#properties()} keyed by tool-call
 * result id, so it survives the intervening no-op jobs that occur while an AHSP round waits for
 * further tool results to arrive. It is rebuilt from the current batch on every call, so ids from
 * an already-consumed round (not present in the current batch) are dropped rather than accumulating
 * forever.
 */
public class ToolCallResultCompletedAtResolver {

  static final String FIRST_SEEN_PROPERTY = "_toolCallResultFirstSeenAt";

  private final Supplier<OffsetDateTime> clock;

  public ToolCallResultCompletedAtResolver() {
    this(OffsetDateTime::now);
  }

  ToolCallResultCompletedAtResolver(Supplier<OffsetDateTime> clock) {
    this.clock = clock;
  }

  public record Resolved(AgentContext agentContext, List<ToolCallResult> toolCallResults) {}

  public Resolved resolve(AgentContext agentContext, List<ToolCallResult> toolCallResults) {
    if (toolCallResults.isEmpty()) {
      return new Resolved(pruneFirstSeen(agentContext, Map.of()), toolCallResults);
    }

    final var firstSeen = readFirstSeen(agentContext);
    final var updatedFirstSeen = new LinkedHashMap<String, OffsetDateTime>();
    final var now = clock.get();

    final var resolvedResults =
        toolCallResults.stream()
            .map(result -> resolve(result, firstSeen, updatedFirstSeen, now))
            .toList();

    return new Resolved(pruneFirstSeen(agentContext, updatedFirstSeen), resolvedResults);
  }

  private ToolCallResult resolve(
      ToolCallResult result,
      Map<String, OffsetDateTime> firstSeen,
      Map<String, OffsetDateTime> updatedFirstSeen,
      OffsetDateTime now) {
    if (result.completedAt() != null) {
      // A: engine timestamp already present
      return result;
    }
    if (result.id() == null) {
      // event result: no cross-job correlation possible/needed
      return result.withCompletedAt(now);
    }

    // B: worker-observed, keep the earliest time this id was seen across repeated/no-op jobs
    final var observedAt = firstSeen.getOrDefault(result.id(), now);
    updatedFirstSeen.put(result.id(), observedAt);
    return result.withCompletedAt(observedAt);
  }

  private AgentContext pruneFirstSeen(
      AgentContext agentContext, Map<String, OffsetDateTime> updatedFirstSeen) {
    final var previous = readFirstSeen(agentContext);
    if (previous.equals(updatedFirstSeen)) {
      return agentContext;
    }
    if (updatedFirstSeen.isEmpty()) {
      final var properties = new LinkedHashMap<>(agentContext.properties());
      properties.remove(FIRST_SEEN_PROPERTY);
      return agentContext.withProperties(properties);
    }
    return agentContext.withProperty(FIRST_SEEN_PROPERTY, serializeFirstSeen(updatedFirstSeen));
  }

  private Map<String, OffsetDateTime> readFirstSeen(AgentContext agentContext) {
    final var raw = agentContext.properties().get(FIRST_SEEN_PROPERTY);
    if (!(raw instanceof Map<?, ?> map)) {
      return Map.of();
    }
    final var result = new LinkedHashMap<String, OffsetDateTime>();
    map.forEach(
        (key, value) ->
            result.put(String.valueOf(key), OffsetDateTime.parse(String.valueOf(value))));
    return result;
  }

  private Map<String, String> serializeFirstSeen(Map<String, OffsetDateTime> firstSeen) {
    final var result = new LinkedHashMap<String, String>();
    firstSeen.forEach((id, timestamp) -> result.put(id, timestamp.toString()));
    return result;
  }
}
