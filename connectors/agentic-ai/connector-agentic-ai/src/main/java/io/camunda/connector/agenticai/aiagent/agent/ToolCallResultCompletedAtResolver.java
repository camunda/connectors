/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * Resolves a concrete {@code completedAt} for every {@link ToolCallResult} at the earliest
 * ingestion point, per ADR 008: the engine timestamp (AHSP {@code outputElement}) is kept if
 * present, otherwise {@code now()} is stamped. Stateless — nothing is persisted across jobs, so a
 * result resolved via the {@code now()} fallback on a job that later turns out to be a no-op gets a
 * fresh {@code now()} again the next time it is resolved.
 */
public class ToolCallResultCompletedAtResolver {

  private final Supplier<OffsetDateTime> clock;

  public ToolCallResultCompletedAtResolver() {
    this(OffsetDateTime::now);
  }

  ToolCallResultCompletedAtResolver(Supplier<OffsetDateTime> clock) {
    this.clock = clock;
  }

  public List<ToolCallResult> resolve(List<ToolCallResult> toolCallResults) {
    if (toolCallResults.isEmpty()) {
      return toolCallResults;
    }
    return toolCallResults.stream().map(this::resolve).toList();
  }

  private ToolCallResult resolve(ToolCallResult result) {
    return result.completedAt() != null ? result : result.withCompletedAt(clock.get());
  }
}
