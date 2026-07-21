/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.common.AgenticAiRecord;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = AgentContext.AgentContextJacksonProxyBuilder.class)
public record AgentContext(
    @RecordBuilder.Initializer("CURRENT_SCHEMA_VERSION") int schemaVersion,
    @RecordBuilder.Initializer("DEFAULT_STATE") AgentState state,
    @Nullable AgentMetadata metadata,
    @RecordBuilder.Initializer(source = AgentMetrics.class, value = "empty") AgentMetrics metrics,
    List<ToolDefinition> toolDefinitions,
    @Nullable ConversationContext conversation,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> properties)
    implements AgentContextBuilder.With {

  /**
   * The current persisted shape of {@code AgentContext} and its conversation content. Bumped
   * whenever a change to the persisted shape requires migrating previously-persisted state on read
   * (see {@code ConversationSchemaMigration}).
   */
  public static final int CURRENT_SCHEMA_VERSION = 1;

  /**
   * Marker for state persisted before {@code schemaVersion} was introduced (Camunda 8.9). Such
   * state is missing the field entirely; it is treated as this version and migrated on read.
   */
  public static final int LEGACY_SCHEMA_VERSION = 0;

  public static final AgentState DEFAULT_STATE = AgentState.INITIALIZING;

  public AgentContext {
    Objects.requireNonNull(state, "Agent state must not be null");
    Objects.requireNonNull(metrics, "Agent metrics must not be null");
    Objects.requireNonNull(toolDefinitions, "Tool definitions must not be null");
  }

  public AgentContext withProperty(String key, Object value) {
    final var properties = new LinkedHashMap<>(properties());
    properties.put(key, value);
    return withProperties(properties);
  }

  public static AgentContext empty() {
    return builder().build();
  }

  public static AgentContextBuilder builder() {
    return AgentContextBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class AgentContextJacksonProxyBuilder extends AgentContextBuilder {}
}
