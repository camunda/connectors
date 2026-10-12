/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.localtool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceKey;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.api.document.DocumentFactory;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What a local tool can read about the agent while it runs. The state is read-only: a tool changes
 * the agent state through the {@link LocalToolResult#withContextUpdate context updates} of its
 * result.
 *
 * @param configuration the configuration of the current invocation
 * @param properties the free-form properties of the agent context (see {@code
 *     AgentContext.properties()}), as of the call
 * @param documents the documents the agent knows, as of the call
 * @param loadedDocumentIds the ids of the documents whose content is part of the conversation the
 *     model reads, as of the call
 * @param agentInstanceKey the key of the agent instance, or {@code null} if there is none yet
 * @param objectMapper the mapper for converting values, for example to bind arguments
 * @param documentFactory creates a new Camunda document, for example from the content a tool
 *     produced; return the document in the content of the result to show it to the model
 */
public record LocalToolContext(
    AgentConfiguration configuration,
    Map<String, Object> properties,
    DocumentRegistry documents,
    Set<String> loadedDocumentIds,
    @Nullable AgentInstanceKey agentInstanceKey,
    ObjectMapper objectMapper,
    DocumentFactory documentFactory) {}
