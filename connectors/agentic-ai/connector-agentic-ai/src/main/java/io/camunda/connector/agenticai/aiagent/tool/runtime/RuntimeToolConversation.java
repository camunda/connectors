/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceKey;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What a runtime tool can read about the conversation of the agent while it runs, as of the call.
 * The view is read-only: a tool changes the agent state through the {@link
 * RuntimeToolResult#withContextUpdate context updates} of its result.
 */
public interface RuntimeToolConversation {

  /** The documents the agent knows. */
  DocumentRegistry documentRegistry();

  /**
   * The ids of the documents whose content is part of the conversation the model reads, as opposed
   * to documents the model sees as a reference only.
   */
  Set<String> documentIdsWithContent();

  /** The key of the agent instance, or {@code null} if there is none yet. */
  @Nullable AgentInstanceKey agentInstanceKey();

  /**
   * The agent context, including the changes the earlier calls made. Its {@link
   * AgentContext#properties() properties} are the free-form state of the agent.
   */
  AgentContext toAgentContext();
}
