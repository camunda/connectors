/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSchemaMigration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import java.io.IOException;
import org.jspecify.annotations.Nullable;

/**
 * Field-level deserializer for the {@code agentContext} request field: migrates a legacy-shaped
 * (pre-{@link AgentContext#CURRENT_SCHEMA_VERSION}) persisted context on read, before binding it.
 *
 * <p>{@code AgentContext} is bound by the connector SDK's own {@code ObjectMapper} via {@code
 * bindVariables}, not by an ObjectMapper this module controls — a field-level
 * {@code @JsonDeserialize} annotation travels into that binding step, whereas a Jackson module
 * registration would not. This deserializer runs inside {@code bindVariables}' own {@code
 * readValue} call (secrets have already been substituted textually beforehand; validation runs
 * afterwards on the resulting object), so there is no separate binding pass and no recursion:
 * {@link ConversationSchemaMigration#migrateAndBindAgentContext} binds via {@code
 * mapper.treeToValue(..., AgentContext.class)}, which uses {@code AgentContext}'s own builder
 * deserializer, not this field deserializer.
 */
public class VersionedAgentContextDeserializer extends JsonDeserializer<AgentContext> {

  @Override
  public @Nullable AgentContext deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {
    return ConversationSchemaMigration.migrateAndBindAgentContext(
        p.readValueAsTree(), (ObjectMapper) p.getCodec());
  }

  @Override
  public @Nullable AgentContext getNullValue(DeserializationContext ctxt) {
    return null;
  }
}
