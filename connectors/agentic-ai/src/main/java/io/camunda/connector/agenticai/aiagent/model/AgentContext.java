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
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.lang.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = AgentContext.AgentContextJacksonProxyBuilder.class)
public record AgentContext(
    @RecordBuilder.Initializer("DEFAULT_STATE") AgentState state,
    @RecordBuilder.Initializer(source = AgentMetrics.class, value = "empty") AgentMetrics metrics,
    List<ToolDefinition> toolDefinitions,
    @Nullable ConversationContext conversation,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> properties)
    implements AgentContextBuilder.With {

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
