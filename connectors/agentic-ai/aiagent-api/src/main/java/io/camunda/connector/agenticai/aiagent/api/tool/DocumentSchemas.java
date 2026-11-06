/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ENUM;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_REQUIRED;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.TYPE_OBJECT;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.TYPE_STRING;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.common.annotation.Experimental;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
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
 * provider as it is. A call whose document parameter does not hold the handle of a known document
 * is rejected before the tool is reached, and the model reads the reason as the result of the call.
 * What the tool receives instead of the handle depends on its kind: a runtime tool gets the {@link
 * io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId} (see {@link
 * RuntimeToolCall}), the process or a gateway tool the Camunda document reference.
 *
 * <p>A parameter that takes a document or something else is a plain {@code anyOf} or {@code oneOf}
 * with more than one alternative, one of them {@link #document document(...)}, directly or inside
 * the properties of an object alternative or the items of an array alternative. Every handle in the
 * value of such a parameter is replaced; all other values reach the tool as they are.
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
    final var type = new LinkedHashMap<String, Object>();
    type.put(PROPERTY_TYPE, TYPE_STRING);
    type.put(PROPERTY_ENUM, List.of(AgentDocumentHandle.TYPE));

    final var id = new LinkedHashMap<String, Object>();
    id.put(PROPERTY_TYPE, TYPE_STRING);
    id.put(PROPERTY_DESCRIPTION, "The id of the document.");

    final var properties = new LinkedHashMap<String, Object>();
    properties.put("type", type);
    properties.put("id", id);

    final var schema = new LinkedHashMap<String, Object>();
    schema.put(PROPERTY_TYPE, TYPE_OBJECT);
    if (StringUtils.isNotBlank(description)) {
      schema.put(PROPERTY_DESCRIPTION, description);
    }
    schema.put(PROPERTY_PROPERTIES, properties);
    schema.put(PROPERTY_REQUIRED, List.of("type", "id"));
    return schema;
  }
}
