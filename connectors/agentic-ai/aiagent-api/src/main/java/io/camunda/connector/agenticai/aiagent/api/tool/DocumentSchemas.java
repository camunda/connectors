/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.common.annotation.Experimental;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Creates the schemas of the parameters of a tool that carry a document. Put the schema into the
 * {@code properties} of the input schema of the {@link ToolDefinition tool definition}: of a {@link
 * RuntimeTool#definition() runtime tool} or of a tool that a {@link GatewayToolHandler} returns
 * from {@link GatewayToolHandler#handleToolDiscoveryResults}.
 *
 * <p>{@link Experimental}, see {@link RuntimeTool}.
 *
 * <p>The schema of a document is the object schema of its {@link AgentDocumentHandle}: the model
 * passes the {@code type} and the {@code id} of a handle it read, and the schema goes to the model
 * provider as it is. For an agent instance that tracks its documents, a call whose document
 * parameter does not hold the handle of a known document is rejected before the tool is reached,
 * and the model reads the reason as the result of the call. What the tool receives instead of the
 * handle depends on its kind: a runtime tool gets the {@link
 * io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId} (see {@link
 * RuntimeToolCall}), a process or gateway tool gets the Camunda document reference.
 *
 * <p>A parameter that takes a document or something else is a plain {@code anyOf} or {@code oneOf}
 * with {@link #document document(...)} among its alternatives, directly or inside the properties of
 * an object alternative or the items of an array alternative. Every handle in the value of such a
 * parameter is replaced; the rest of the value reaches the tool as it is.
 */
@Experimental
public final class DocumentSchemas {

  private DocumentSchemas() {}

  /**
   * The schema of a parameter that is always a document: an object with the properties {@code type}
   * (the handle type) and {@code id}, both required.
   *
   * @param description what the parameter is for, shown to the model, or {@code null}
   * @return an insertion-ordered map
   */
  public static Map<String, Object> document(@Nullable String description) {
    return DocumentParameter.documentSchema(description);
  }
}
