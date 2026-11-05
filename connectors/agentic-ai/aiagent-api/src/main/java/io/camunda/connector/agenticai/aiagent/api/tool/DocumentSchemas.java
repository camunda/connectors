/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_ANYOF;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_FORMAT;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.TYPE_STRING;

import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.common.annotation.Experimental;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Creates the schemas of the parameters of a tool that carry a document, so that nobody writes the
 * format that marks them by hand. Put the schema into the {@code properties} of the input schema of
 * the {@link ToolDefinition tool definition}: of a {@link RuntimeTool#definition() runtime tool} or
 * of a tool that a {@link GatewayToolHandler} returns from {@link
 * GatewayToolHandler#handleToolDiscoveryResults}.
 *
 * <p>{@link Experimental}, see {@link RuntimeTool}.
 *
 * <p>The agent adds a sentence to the description that tells the model to pass the tag of a known
 * document, and takes the format away, before the schema goes to the model provider. The stored
 * definition keeps the schema as it is created here. The model passes a document as its {@code <doc
 * id="..." />} tag. A call whose document parameter does not hold the tag of a known document is
 * rejected before the tool is reached, and the model reads the reason as the result of the call.
 * What the tool receives instead of the tag depends on its kind: a runtime tool gets the {@link
 * io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId} (see {@link
 * RuntimeToolCall}), the process or a gateway tool the Camunda document reference.
 */
@Experimental
public final class DocumentSchemas {

  private DocumentSchemas() {}

  /**
   * The schema of a parameter that is always a document: a string with the format {@link
   * DocumentParameter#FORMAT_DOCUMENT_ID}.
   *
   * @param description what the parameter is for, shown to the model, or {@code null}
   * @return an insertion-ordered map
   */
  public static Map<String, Object> document(@Nullable String description) {
    final var schema = new LinkedHashMap<String, Object>();
    schema.put(PROPERTY_TYPE, TYPE_STRING);
    schema.put(PROPERTY_FORMAT, DocumentParameter.FORMAT_DOCUMENT_ID);
    if (StringUtils.isNotBlank(description)) {
      schema.put(PROPERTY_DESCRIPTION, description);
    }
    return schema;
  }

  /**
   * The schema of a parameter that is text or a document: an {@code anyOf} of a plain string and
   * the {@link #document document schema}. The tool receives a document when the model passed
   * exactly one document tag and the text otherwise.
   *
   * @param description what the parameter is for, shown to the model, or {@code null}
   * @return an insertion-ordered map
   */
  public static Map<String, Object> textOrDocument(@Nullable String description) {
    final var text = new LinkedHashMap<String, Object>();
    text.put(PROPERTY_TYPE, TYPE_STRING);
    final var schema = new LinkedHashMap<String, Object>();
    if (StringUtils.isNotBlank(description)) {
      schema.put(PROPERTY_DESCRIPTION, description);
    }
    schema.put(PROPERTY_ANYOF, List.of(text, document(null)));
    return schema;
  }
}
