/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.common.AgenticAiRecord;
import io.camunda.connector.api.outbound.JobContext;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Metadata about an AI Agent's execution context, used for detecting process definition migrations.
 *
 * @param processDefinitionKey The key of the process definition this agent was initialized with
 * @param processInstanceKey The key of the process instance this agent is executing in
 * @param agentInstanceKey The key of the agent instance created on the engine, if any
 * @param lastIterationKey The highest turn iterationKey persisted so far, if any. Authoritative
 *     counter for the next turn's iterationKey; absent for conversations created before this field
 *     was introduced, or right after a process definition migration reset.
 * @param configurationFingerprintHistory The turn iterationKey at which {@link
 *     AgentConfiguration#fingerprint()} changed, mapped to the fingerprint it changed to. Only
 *     entries where it actually changed are recorded (not one per turn), so the fingerprint
 *     effective at any given iteration is the value of the entry with the largest key at or before
 *     it. Empty for conversations created before this field was introduced.
 */
@AgenticAiRecord
public record AgentMetadata(
    Long processDefinitionKey,
    Long processInstanceKey,
    @Nullable Long agentInstanceKey,
    @Nullable Integer lastIterationKey,
    Map<Integer, String> configurationFingerprintHistory)
    implements AgentMetadataBuilder.With {

  /**
   * Convenience constructor for callers that don't care about {@link
   * #configurationFingerprintHistory()} (e.g. most existing tests); defaults it to empty.
   */
  public AgentMetadata(
      Long processDefinitionKey,
      Long processInstanceKey,
      @Nullable Long agentInstanceKey,
      @Nullable Integer lastIterationKey) {
    this(processDefinitionKey, processInstanceKey, agentInstanceKey, lastIterationKey, Map.of());
  }

  public static AgentMetadata of(JobContext jobContext) {
    return new AgentMetadata(
        jobContext.getProcessDefinitionKey(),
        jobContext.getProcessInstanceKey(),
        null,
        null,
        Map.of());
  }

  /**
   * Returns the {@link AgentConfiguration#fingerprint()} that was effective as of the given
   * iteration, or {@code null} if none is recorded at or before it.
   */
  public @Nullable String configurationFingerprintAt(int iterationKey) {
    return configurationFingerprintHistory.entrySet().stream()
        .filter(entry -> entry.getKey() <= iterationKey)
        .max(Map.Entry.comparingByKey())
        .map(Map.Entry::getValue)
        .orElse(null);
  }
}
